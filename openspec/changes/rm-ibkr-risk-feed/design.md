## Context

見 proposal.md 的 Why。現況：

- 秒級計時器寫 `IBGateway:{name}:status`。工作階段還在時另寫 `{name}_CONNECTION`（TTL 2 秒）。判斷工作階段用 `isRealConnected()`。
- `AllOrderHandler` 把 OMS JSON 寫進 `URANUS:*`。`toOMSJSON()` 把數量收成整數、價格只用限價。`isAlive()` 對 `Inactive` 會看 orderRef 前綴。`openOrderEnd` 就初始化 OMS，不等完結單。
- `ApiController.reqCompletedOrders` 已呼叫 `reqCompletedOrders(false)`。
- `AccountMVHandler` 在初始下載之後，每次現金或持倉回呼都寫 `IBGateway:{account}:balance`，張數用 `longValue()`。`runAccountMvCycle` 對每戶 `reqAccountUpdates(true)` 後固定睡 3 秒，不送 `false`，也不等 `accountDownloadEnd`。下一戶的 `true` 會取消上一戶。30 秒一輪，上一輪還在跑就略過。結束後若 focus 不是最後一戶，再訂一次 focus。
- `IBContract.pair()` 在期貨乘數為 1 時省略 `@乘數`，日期用 `lastTradeDateOrContractMonth`。
- `Redis.set` / `pub` 用預設 fastjson，`null` 欄位會被省略。現有 wrapper 沒有 hash 操作。

契約要求見 `specs/rm-ibkr-risk-feed/spec.md` 與 `doc/ibkr_risk_feed.md`。

## Goals / Non-Goals

**Goals:**

- 新鍵的可觀察行為與規格一致，包含先寫 hash 再推送、重連先推再刪、完整影像才寫餘額與摘要。
- 既有鍵的格式與觸發時機保持現狀。帳戶輪詢只改「何時換下一戶」。

**Non-Goals:**

- 不改 `src/main/java/com/ib/`。
- 不把風控 JSON 塞進 OMS，也不讓 OMS 改走新的狀態表。
- 不為餘額補逐筆或 PnL。不在閘道裡計算數量級，也不暫停下單。
- 不把從未進過 `open` 的完結單補推出去。
- 不新增測試以外的文件改寫。`doc/ibkr_risk_feed.md` 已是契約，實作時不改它。

## Decisions

### 1. 風控寫入與 OMS、舊餘額分開

新增兩條旁路，由現有回呼在做完既有寫入之後呼叫：

- 訂單旁路接收 `openOrder`、`orderStatus`、錯誤碼造成的拒絕或撤銷、`completedOrder`、成交與佣金、合約明細補齊。
- 帳戶旁路只接收帳戶數值、持倉與 `accountDownloadEnd`。舊的 `writePosition()` 仍在每次回呼時寫 `IBGateway:{account}:balance`。

旁路自己組 JSON。不呼叫 `toOMSJSON()`、`isAlive()`、`IBContract.pair()`。

選擇分開，是因為 OMS 的死活與 pair 規則和這份契約相反。把判斷寫進 `IBOrder` 會讓 URANUS 一起改。

### 2. 序列化必須留下 null

風控 JSON 用 `SerializerFeature.WriteMapNullValue` 產出字串，再 `SET`、`HSET` 或 `PUBLISH` 這段字串。不走 `Redis.set(Object)` / `Redis.pub(Object)`，避免預設 fastjson 吃掉 `null`。`PUBLISH` 的內容與 hash 裡同一份字串，是一個 JSON 物件，不再包一層引號。

未設定的價格（回呼裡的 `0`，以及 IB 訂單上的 `Double.MAX_VALUE`）都寫成 JSON `null`。

### 3. 訂單時間與終態順序

記憶體保留每個 `orderRef` 最後的 `updateTimeMs`。新的一筆若時鐘不大於它，就用它加 1。進程重啟後，寫入前先讀 hash 裡舊值，避免重連對帳把時間倒退。

連線期間：`new` / `cancelling` 先 `HSET` 再 `PUBLISH`。終態先 `HDEL` 再 `PUBLISH`。佣金晚到時只 `PUBLISH`，不 `HSET`。`WarnState` 不呼叫寫入。

`orderRef`：`uranus_` 或 `api_` 原樣；否則 `tws:` + 十進位 permId；permId 為 0 直接返回。

### 4. 重連對帳獨立於 OMS 初始化

訂單旁路自己等兩個結束信號：這次連線的 `openOrderEnd`，以及 `completedOrdersEnd`。OMS 仍在 `openOrderEnd` 初始化，不改那條路徑。

兩個信號都到了，才取出該帳戶 `open` 裡有、但這次未完成單快照沒有的 field。用當日完結單與當日成交對表，先 `PUBLISH` 再 `HDEL`。完結單請求維持現有的 `apiOnly=false`。

成交均價與累計量留在這次連線的記憶體，不從 `TradeReport:*` 反查，避免和另一份 JSON 形狀耦合。

### 5. 帳戶輪詢改為等到影像結束

`runAccountMvCycle` 對每一戶：

1. 若已有訂閱，先 `reqAccountUpdates(false, 上一戶)`。
2. 開始該戶的風控組裝緩衝（清掉這一輪，不碰舊餘額的 `_data`）。
3. `reqAccountUpdates(true, 該戶)`。
4. 等到該戶的 `accountDownloadEnd`，或 60 秒逾時，或連線斷掉。
5. 只有 End 到達、這輪沒有 `AccountReady=false`、且有無後綴的 `NetLiquidation`，才 `SET` 兩個 `RM:IBKR` 鍵。逾時或未就緒則不寫，緩衝丟棄。

focus 帳戶若不是本輪最後一個，同樣走這五步，然後這輪才算結束。`accountMvCycleRunning` 仍擋住 30 秒計時器與 `UPDATE_ACCOUNT_MV`，不並行第二輪。等待發生在帳戶輪詢執行緒，不在秒級心跳計時器裡。

60 秒是卡住時的上限，不是契約裡的更新週期。影像通常更早結束；超過就放棄這一戶的新鍵，舊鍵已在回呼裡更新過。

替代方案是維持睡 3 秒。那樣下一戶的訂閱會取消尚未結束的影像，新鍵會長期不更新，所以不採用。

### 6. 連線鍵掛在現有秒級計時器上

同一個計時器裡，若 `isRealConnected()` 且距離上次風控心跳已滿 5 秒，就 `SETEX` `RM:IBKR:{name}:connection` 15 秒。不 `sleep`。工作階段不在就不寫。`{name}_CONNECTION` 與 `IBGateway:{name}:status` 保持原樣。

### 7. pair 與持倉 key 另做

風控 pair 按契約組：期貨與期權用 8 碼 `lastTradeDate`，一律帶 `@乘數`；股票與 `CASH` 只用 `{currency}-{symbol}`。持倉 key 按規格的五列條件組，避免空段。張數用 `Decimal` 的字串轉數字，不用 `longValue()`。部位為 0 的列不進 JSON。

合約明細之後才齊時，訂單旁路再推一筆。明細回呼已經會更新 `IBContract`；補推點放在明細寫入快取之後，只針對仍需要更新 pair 的 `orderRef`。

## Risks / Trade-offs

- [輪詢改等 End，舊餘額換戶變慢] → 舊鍵仍在每次回呼寫入；被訂著的那一戶不會停在連線當下。30 秒到了若這輪未完就略過，與現有互斥相同。
- [60 秒逾時把該戶新鍵留在上一份] → 契約允許影像沒收完就不前進 `updateTimeMs`。下一輪會再試。
- [重連時完結單與未完成單結束順序不定] → 對帳屏障等兩邊，避免提早 `removed`。
- [fastjson 預設省略 null，讀者會把缺欄當成沒有規定] → 風控字串單獨序列化，並用一筆含 `null` 價格的訂單做斷言。
- [`AccountReady=false` 時舊餘額仍可能寫入不完整數字] → 這是既有行為，本變更不改它。新鍵必須跳過該輪。
- [permId 為 0 的手動單不會出現] → 契約已排除。不另造 `orderRef`。

## Migration Plan

部署後新鍵開始出現。`connection` 有 TTL，舊進程停寫後約 15 秒消失。餘額與摘要沒有 TTL，回滾程式後會留著最後一輪；讀者以連線鍵是否存在判斷工作階段。不需要遷移既有鍵。回滾就是還原閘道程式，不刪舊鍵。

## Open Questions

無。逾時 60 秒若實測常態下載更久，只改這個常數，不改規格。
