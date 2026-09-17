## Context

見 `proposal.md` 的 Why。現況約束：

- IB `reqAccountUpdates` 同時只能訂一個帳；`subscribeAccountMV` 以 3 秒間隔輪詢 `accList` 後再訂回 `focusAccount`，且只在 `_postConnected` 跑一次。註解「streaming 1~5s」與現場不符。
- 9/2 曾改 `reqAccountUpdatesMulti` + `reqPositionsMulti`，因沒有 `marketPrice`／`marketValue`／PnL 而改回 `reqAccountUpdates`。
- `subscribeAccountMV` 在呼叫執行緒上 `sleep(3000)`；`UPDATE_ACCOUNT_MV` 已用 `new Thread` 包一輪。秒級心跳是另一條 `GatewayControllerLiveStatusWriter`。
- `reqAccountSummary` 同時最多兩條訂閱，超過 error 322。`queryAccountSummary` 在 `accountSummaryActive` 時略過重送。TWS 第一次 snapshot 後送 `accountSummaryEnd`，之後最多每 3 分鐘只推有變的 tag，不再送 End。
- `AccountSummaryHandler` 只在 `accountSummaryEnd` 寫 `IBGateway:Summary:{account}`；`accountSummary()` 只改 RAM `m_map`。

## Goals / Non-Goals

**Goals:**

- 市值刷新與心跳隔離，輪詢 `sleep` 不得卡住 `IBGateway:{name}:status`。
- 連線後、定時、`UPDATE_ACCOUNT_MV` 三路對 TWS 的市值請求序列化。
- `_postConnected` 不被市值輪詢的 `sleep` 擋住後續的摘要查詢與訂單刷新。
- 摘要增量寫 Redis 時，不把尚未結束的 snapshot 寫出去；重連後的新 snapshot 同樣等到 End。

**Non-Goals:**

- 不定時 `queryAccountSummary`，不拿掉 FIND skip。
- 不改 `AccountMVHandler` 的 Redis 寫入時機（仍是每次 cash／持倉回調就寫）。
- 不改 Redis key／TTL、ACK 格式、`UPDATE_FOCUS_ACCOUNT` 指令格式。
- 不在成交後強制重訂市值。

## Decisions

### 1. 獨立 Timer 每 30 秒嘗試一輪，不與心跳共用

- **選擇**：另開 `Timer`（daemon），period 30 秒。Task 只負責「若已連線且拿到互斥則丟到 worker」。心跳 `GatewayControllerLiveStatusWriter` 不呼叫市值邏輯。
- **理由**：一輪成本是 `帳號數 * 3s`，掛在 1 秒心跳上會讓 status 停更。`scheduleAtFixedRate` 在互斥略過時不會堆積工作。
- **替代**：跟心跳同一條 Timer → 否決（違反心跳不得停頓）。`ScheduledExecutorService` → 可以，但專案現有模式是 `java.util.Timer`，少引入新排程器。

排程形狀：

```
GatewayControllerLiveStatusWriter (1s)
    --> Redis status / ACK heartbeat
        (never reqAccountUpdates)

GatewayControllerAccountMVRefresh (30s)
    |
    +-- !isConnected()           --> skip
    +-- 互斥已被持有             --> skip
    +-- else                     --> worker 跑一輪
```

連線後第一輪仍由 `_postConnected` 觸發，不等第一個 30 秒。Timer 延遲 30 秒再開始即可，避免與第一輪搶跑（即使搶跑也由互斥略過）。

### 2. 一輪市值刷新都進同一把互斥與 worker，呼叫端不 sleep

- **選擇**：抽出單一入口（連線後、Timer、`UPDATE_ACCOUNT_MV` 都走它）。用 `AtomicBoolean`（或同等互斥）標「進行中」：Timer 與連線後 `tryLock` 失敗就略過；`UPDATE_ACCOUNT_MV` 也不得另開一輪並行（拿不到鎖則略過並打 log，ACK 仍送）。實際 `reqAccountUpdates` + `sleep(3000)` 只在 worker 執行緒跑，結束時若 `focusAccount` 不是最後一個帳則再訂一次。`_postConnected` 改為非阻塞觸發，接著立刻 `queryAccountSummary`／刷新訂單。
- **理由**：現況連線 Timer 會被 `subscribeAccountMV` 擋住數十秒；`UPDATE_ACCOUNT_MV` 已是獨立 Thread，若不互斥會跟定時輪詢搶「當前訂閱帳」。略過比排隊簡單，避免 Timer 與手動更新疊出一串 3 秒輪詢。
- **替代**：`UPDATE_ACCOUNT_MV` 排隊等到鎖 → 否決（指令可能在數十秒後才打 TWS，語意難解釋）。取消上一輪再跑新的 → 否決（`reqAccountUpdates` 沒有乾淨的「取消這一輪」；硬切會丟 snapshot）。

`UPDATE_FOCUS_ACCOUNT` 仍立即改 `focusAccount`；若當下沒有進行中的輪詢則直接 `reqAccountUpdates`。進行中的輪詢結束時讀當下的 `focusAccount` 再訂回，不必跟輪詢搶鎖。

Worker 在帳戶間等待時若已斷線，MUST 結束本輪並釋放互斥。

### 3. 摘要：snapshot 完成旗標，增量只寫該帳戶

- **選擇**：`AccountSummaryHandler` 加「初始 snapshot 已結束」旗標。`accountSummary()` 先更新 `m_map`；旗標為真才把該帳戶寫 Redis（格式與 End 相同：`data` + `updateTime`）。`accountSummaryEnd()` 先把旗標設真，再寫全部帳戶。`queryAccountSummary` 在真正 `reqAccountSummary` 之前把旗標清掉。斷線只清 `accountSummaryActive`；重連後的 `queryAccountSummary` 會清旗標，因此新 snapshot 不會被當成增量。
- **理由**：TWS 增量不再送 End；只改 End 寫入解決不了 Redis 凍結。第一次／重連後的 snapshot 是一串欄位回調，提前寫會讓下游讀到半套。
- **替代**：每次 `accountSummary()` 都寫 → 否決（半套）。定時 cancel+req 換新 End → 否決（本次明確先不用，且 322）。把 `m_map` 改 `ConcurrentHashMap` → 不必要，回調與 End 已在同一條 inbound 執行緒。

```
queryAccountSummary (非 skip)
        |
        v
  snapshotComplete = false
  reqAccountSummary
        |
        v
  accountSummary() --> 只改 m_map
  accountSummaryEnd() --> snapshotComplete = true, 寫全部帳戶
        |
        v
  之後的 accountSummary() --> 改 m_map, 寫該帳戶
```

FIND 已訂閱時維持 skip，不清旗標、不重送。

## Risks / Trade-offs

- [帳號很多時一輪超過 30 秒，定時每次都略過] → 接受；有效間隔變成一輪時長。不在本次動態調 delay。
- [`UPDATE_ACCOUNT_MV` 撞上定時輪詢被略過] → 打 log；下游可再送。不排隊。
- [TWS 摘要增量實際沒推，Redis Summary 仍停在 End] → 接受；本次不定時重查。若現場仍凍，另開 change。
- [重連後若忘記清 snapshot 旗標，新 snapshot 會寫半套] → `queryAccountSummary` 在 req 前必清；任務需覆蓋此路徑。
- [Timer 與 `_postConnected` 同時 tryLock] → 一個跑、一個略過；連線後那輪優先的機率高，可接受。

## Migration Plan

- 佈署後重啟 gateway，讓新 Timer 與 handler 生效。
- 回滾：還原 `GatewayController.java`、`AccountSummaryHandler.java` 並重啟。
- 驗證：連線後 `IBGateway:{account}:balance` 與 `IBGateway:Summary:{account}` 有值；30 秒後 balance 再刷新（log 可見新一輪 `Req account mv`）；摘要在 End 之後若有欄位回調，該帳戶 `updateTime` 前進且不必再發 FIND。FIND 已訂閱時 log 仍為 skip。心跳在市值輪詢期間仍每秒寫入。
