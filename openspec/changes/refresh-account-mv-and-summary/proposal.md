## Why

`subscribeAccountMV` 只在連線時跑一輪。IB `reqAccountUpdates` 同時只能訂一個帳，輪詢後只剩最後／focus 帳，且該串流實務上常停；`IBGateway:{account}:balance` TTL 很長，下游會一直讀到過期市值。帳戶摘要方面，TWS 在第一次 snapshot 的 `accountSummaryEnd` 之後，最多每 3 分鐘只再推有變的 tag，但 `AccountSummaryHandler` 只在 End 寫 Redis，增量只留在記憶體，`IBGateway:Summary:{account}` 停在連線當下。

## What Changes

- 連線後立刻跑一輪 `subscribeAccountMV`，之後每 30 秒再跑（dedicated Timer，不與 heartbeat 共用）。未連線略過；上一輪（`帳號數 * 3s`）尚未結束則略過整輪。
- Redis 指令 `UPDATE_ACCOUNT_MV` 與定時輪詢共用同一把進行中鎖，避免兩路同時 `reqAccountUpdates`。
- `AccountSummaryHandler`：第一次 `accountSummaryEnd` 之後，增量 `accountSummary()` 也寫該帳戶的 `IBGateway:Summary:{account}`，`updateTime` 為寫入當下。End 仍寫全部帳戶。第一次 End 之前不寫半套 snapshot。
- **不**定時重查 `queryAccountSummary`；`FIND_ACCOUNT_SUMMARY` 在已訂閱時仍略過重送（避免 error 322）。
- **不**改回 `reqAccountUpdatesMulti`／`reqPositionsMulti`（沒有市值）。
- **不**在成交後強制重訂 MV。

## Capabilities

### New Capabilities

- `account-mv`：帳戶市值如何向 TWS 取值，以及 Redis `IBGateway:{account}:balance` 何時刷新——連線後與之後每 30 秒 MUST 重跑 `subscribeAccountMV`（輪詢各帳後訂回 focus）。
- `account-summary`：帳戶摘要如何向 TWS 取值，以及 Redis `IBGateway:Summary:{account}` 何時刷新——初始 snapshot 的 End 與之後的增量 `accountSummary()` MUST 都寫入 Redis；已訂閱時 FIND 仍略過重送。

### Modified Capabilities

- （無。`openspec/specs/account-summary/` 目前沒有 spec.md。）

## Impact

- `src/main/java/com/avalok/ib/GatewayController.java`（MV 定時器、進行中鎖、連線／斷線）
- `src/main/java/com/avalok/ib/handler/AccountSummaryHandler.java`（增量寫 Redis）
- Redis key 名稱與 TTL 不變：`IBGateway:{account}:balance`、`IBGateway:Summary:{account}`
- `UPDATE_ACCOUNT_MV`、`UPDATE_FOCUS_ACCOUNT`、`FIND_ACCOUNT_SUMMARY` 的指令格式與 ACK 不變；FIND 略過語意不變
- 無新依賴；無 **BREAKING** API 變更
