## Purpose

定義閘道寫給風控的 Redis 鍵：連線心跳、未完成單與訂單推送、以及只在完整帳戶影像收完後更新的餘額和摘要。欄位名稱與範例以 `doc/ibkr_risk_feed.md` 為準。

## ADDED Requirements

### Requirement: 既有鍵格式不變
系統 SHALL 繼續依現有方式寫入既有 Redis 鍵。本能力 MUST NOT 改變那些鍵的名稱、TTL 或 JSON 形狀。新增的鍵只使用 `RM:IBKR:` 前綴。

#### Scenario: 舊餘額仍在回呼時寫入
- **WHEN** 帳戶市值回呼在初始下載之後更新現金或持倉
- **THEN** `IBGateway:{account}:balance` MUST 仍按既有格式寫入，且 `RM:IBKR:{account}:balance` MUST NOT 因這次增量回呼而更新

### Requirement: 連線心跳
`{gateway}` 是這個 gateway 進程的名稱。IB 工作階段還在時，系統 SHALL 約每 5 秒覆寫 `RM:IBKR:{gateway}:connection`，值是一個 JSON 物件，必有 `updateTimeMs`（UTC Unix 毫秒整數），TTL 15 秒。工作階段已斷時，系統 MUST 停止覆寫，讓鍵依 TTL 消失。進程還在但工作階段已斷，MUST NOT 繼續覆寫。

#### Scenario: 工作階段還在
- **WHEN** IB 工作階段仍在，且距離上次覆寫已約 5 秒
- **THEN** 該鍵 MUST 被覆寫，`updateTimeMs` 為這次心跳的毫秒，TTL 為 15 秒

#### Scenario: 工作階段已斷
- **WHEN** 進程仍在但 IB 工作階段已經斷開
- **THEN** 系統 MUST NOT 再覆寫該鍵

### Requirement: 訂單身份與時間
同一張 IB 單的 `orderRef` MUST 始終相同。下單請求帶有 orderRef 時原樣寫回。沒有 orderRef 的手動單 MUST 寫成 `tws:` 加十進位 permId。permId 仍是 0 時，系統 MUST NOT 寫入 `open`，也 MUST NOT 推送。同一 `orderRef` 的 `updateTimeMs` MUST 嚴格大於上一筆；同一毫秒時後一筆 MUST 寫成上一筆加 1。數字 MUST 是 JSON number。契約允許 JSON `null` 的欄位 MUST 出現且值為 `null`，MUST NOT 省略，也 MUST NOT 用空字串或 `0` 代替。

#### Scenario: permId 仍是 0
- **WHEN** 一張手動單的 permId 仍是 0
- **THEN** 系統 MUST NOT 寫入該單的 hash 欄位，也 MUST NOT 推送

#### Scenario: 同一毫秒兩筆
- **WHEN** 同一 `orderRef` 在同一毫秒內要寫第二筆
- **THEN** 第二筆的 `updateTimeMs` MUST 等於上一筆加 1

### Requirement: 未完成單與即時推送
`RM:IBKR:{account}:open` 是 HASH，field 是 `orderRef`，value 是訂單 JSON 字串。`RM:IBKR:{account}:order` 是 PUB/SUB，訊息是一個 JSON 物件，不是再包一層字串。`open` MUST 只保留 `status` 為 `new` 或 `cancelling` 的單。連線期間，留在 `open` 的更新 MUST 先寫 hash 再推送；終態 MUST 先從 hash 刪除再推送。推送 MUST 含該筆完整訂單欄位。未完成單 MUST 滿足 `remainedQty = qty - filledQty`。`filled`、`cancelled`、`rejected` 的 `remainedQty` MUST 是 0，`qty` MUST 仍是原始委託數量。`removed` MUST 沿用刪除前的 `remainedQty`。`removed` MUST 只在重連對帳出現。

狀態對應 MUST 為：`ApiPending`、`PendingSubmit`、`PreSubmitted`、`Submitted`、`Inactive`、`Unknown` 寫 `new` 並保留；`PendingCancel` 與 `PreCancelled` 寫 `cancelling` 並保留；`Filled` 寫 `filled`；`Rejected` 寫 `rejected`；`Cancelled`、`Canceled`、`ApiCancelled` 寫 `cancelled`。`PreCancelled` MUST NOT 寫成 `cancelled`。`WarnState` MUST NOT 寫成 `cancelled` 或 `rejected`，也 MUST NOT 改變該單既有的 `status`。

#### Scenario: 撤銷尚未確認
- **WHEN** IB 狀態是 `PendingCancel` 或 `PreCancelled`
- **THEN** hash 中該單的 `status` MUST 是 `cancelling`，且 MUST 仍留在 `open`

#### Scenario: 連線期間完全成交
- **WHEN** 連線期間 IB 狀態變成 `Filled`
- **THEN** 系統 MUST 先刪除該 field，再推送 `status` 為 `filled`、`remainedQty` 為 0 的訊息

#### Scenario: WarnState
- **WHEN** 已在 `open` 的單收到 `WarnState`
- **THEN** 該單 MUST 留在 `open`，且 `status` MUST 與收到前相同

### Requirement: 價格與明細未齊
`price` 為 `null` 表示沒有可用來乘數量的價格。`LMT` MUST 用 `lmtPrice`。`MKT`、`TRAIL`、`TRAIL LIMIT` 的 `price` MUST 是 `null`。`TRAIL` MUST NOT 把 `auxPrice`、`trailingPercent`、`trailStopPrice` 寫進 `price`。`TRAIL LIMIT` MUST NOT 把 `lmtPrice`、`lmtPriceOffset`、`auxPrice`、`trailStopPrice` 寫進 `price`。這兩個 `orderType` MUST 保持 IB 大寫原文，MUST NOT 併成同一個字。`STP` MUST 用觸發價。`STP LMT` 與其他帶限價的類型 MUST 用限價，沒有限價則 `null`。`BAG` 有整組淨限價就寫該淨價，可以是負數，沒有才 `null`。回呼裡未設定的 `0` MUST 寫成 `null`。

明細未齊時系統 MUST 仍寫入並推送。股票與 `CASH` 能組成 `{currency}-{symbol}` 就寫 `pair`，MUST NOT 等待主上市地。主上市地未知或只是 `SMART` 時，訂單的 `exchange` MUST 是 `null`。期貨沒有主上市地時 MUST 用合約交易所。期貨與期權的 `lastTradeDate` 還不是 8 碼時，`pair` MUST 是 `null`；日期 MUST 用 `lastTradeDate`，MUST NOT 用 `realExpirationDate`。補齊後 MUST 用同一個 `orderRef` 再推一筆，且 `updateTimeMs` 更大。股票乘數沒給 MUST 寫 `1`。其他類型沒給 MUST 寫 `null`。期貨與期權的 pair MUST 含 `@乘數`，即使乘數是 1。`strike` MUST 固定一位小數。`qty` 與 `filledQty` MUST 保留 IB 的小數。

#### Scenario: 追蹤單沒有可乘價格
- **WHEN** `orderType` 是 `TRAIL` 或 `TRAIL LIMIT`
- **THEN** 推送的 `price` MUST 是 JSON `null`，且 `orderType` MUST 分別是這兩個原文

#### Scenario: 期貨日期未齊
- **WHEN** 期貨單的 `lastTradeDate` 還不是 8 碼
- **THEN** 系統 MUST 仍推送該單，且 `pair` MUST 是 `null`

### Requirement: 組合單
`BAG` MUST 以父單一個 `orderRef` 寫入 `open` 並推送，並帶 `comboLegs`。沒有腿的單 MUST NOT 帶這個欄位。腿的 `qty` MUST 是父單數量乘該腿比例。腿價沒給 MUST 是 `null`。成交後的持倉 MUST 按腿出現，餘額 JSON MUST NOT 另加一列 `BAG`。

#### Scenario: 組合單父單
- **WHEN** 一張未完成組合單被推送
- **THEN** 訊息 MUST 有一個父單 `orderRef` 與 `comboLegs`，且 `secType` MUST 是 `BAG`

### Requirement: 佣金晚到
終態那一拍若尚未收到佣金回報，`commission` MUST 是 0。之後同一 `orderRef` 收到佣金時，系統 MUST 再推一筆，`commission` 為合計，`updateTimeMs` 更大，且 MUST NOT 把該單寫回 `open`。兩筆回報幣別不同時 MUST NOT 把金額相加，`commissionCurrency` MUST 留第一個非空幣別。沒有後續回報則維持 0。

#### Scenario: 成交之後才收到佣金
- **WHEN** 單已從 `open` 刪除，隨後收到該 `orderRef` 的佣金回報
- **THEN** 系統 MUST 再推一筆較新的 `updateTimeMs`，且 `open` MUST NOT 再出現該 field

### Requirement: 重連對帳
重連後，系統 MUST 等未完成單快照結束，且當日完結單結束，才處理仍留在 `open`、但新的未完成單快照裡已經沒有的 `orderRef`。完結單請求 MUST 包含 TWS 送出的單。完結單與成交回報都只涵蓋當日；成交回報只涵蓋當日午夜之後。

對得上當日完結單 `Filled` 時 MUST 推 `filled`：`filledQty` 優先用完結單，其次當日成交累計，再其次刪除前的值；`avgPrice` 用成交回報均價，沒有則 `null` 而不是 `0`；`remainedQty` 為 0。對得上 `Cancelled` 或 `ApiCancelled`（含零成交撤銷）時 MUST 推 `cancelled`。對得上 `Rejected` 時 MUST 推 `rejected`。兩者的 `remainedQty` 都是 0。都對不上時 MUST 推 `removed`，並帶上刪除前最後一次的欄位，當時是 `null` 的仍是 `null`。`removed` 的 `filledQty` MUST 是斷線前最後看到的量。重連對帳 MUST 先推送再刪除 hash。從未寫入該 hash 的完結單 MUST NOT 因此被補推。

#### Scenario: 完結單尚未到齊
- **WHEN** 未完成單快照已結束，但當日完結單尚未結束
- **THEN** 系統 MUST NOT 把仍留在 `open` 的單推成 `removed`

#### Scenario: 當日拒絕
- **WHEN** 對帳時該單不在新的未完成單快照，且當日完結單狀態是 `Rejected`
- **THEN** 系統 MUST 先推送 `rejected`，再刪除該 field

#### Scenario: 跨日斷線
- **WHEN** 斷線跨過午夜，該單既不在當日完結單也不在當日成交回報
- **THEN** 系統 MUST 推送 `removed`，且 MUST NOT 另查更早的歷史

### Requirement: 餘額與摘要只在完整影像寫入
系統同時 MUST 只維持一個帳戶的帳戶更新訂閱。再訂另一個帳戶前 MUST 停止上一個。每次開始訂閱會收到全量，以 `accountDownloadEnd` 結束。只有該戶這輪收完，且這輪沒有出現 `AccountReady=false`，系統才 MUST 寫入 `RM:IBKR:{account}:balance` 與 `RM:IBKR:{account}:summary`，兩者的 `updateTimeMs` MUST 相同，且是寫出這份完整快照的時間。沒有 `accountDownloadEnd`，或出現 `AccountReady=false`，兩個鍵 MUST 保持上一份，`updateTimeMs` MUST NOT 前進。成交後的現金與張數 MUST NOT 在下一份完整快照之前進入這兩個 JSON。系統 MUST NOT 為這兩個鍵另走一次帳戶摘要。

`cash.*.balance` MUST 是該幣別 `CashBalance`，MUST NOT 使用 `AvailableFunds`、`NetLiquidation` 或 `TotalCashBalance`。`BASE` 與空幣別 MUST NOT 寫進 `cash`。沒有現金時 `cash` MUST 是 `{}`。`netLiquidation` MUST 是沒有 `-S` 或 `-C` 後綴的 `NetLiquidation`。`baseCurrency` MUST 是該列自己的 currency，MUST NOT 假設是 `USD`。這輪沒有該列時，餘額與摘要都 MUST NOT 更新。

持倉 `pos` MUST 保留小數，可以是負數，且 MUST NOT 為 0。沒有市價時 `marketPrice` 與 `marketValue` MUST 是 `null`，MUST NOT 是 0。沒有持倉時 `positions` MUST 是 `{}`。這份 JSON MUST NOT 含 `openCount`。委託 MUST NOT 因為寫進 `open` 就多一列持倉。

多個帳戶輪流時，系統 MUST 先停止上一個訂閱，再開始下一個，並等到該戶 `accountDownloadEnd` 或確認這輪不會收完，才換下一戶。一輪進行中 MUST NOT 並行第二輪。秒級既有心跳 MUST NOT 因等待影像而停頓。若已指定 focus 帳戶且它不是本輪最後一個，本輪結束後 MUST 再訂閱該 focus 帳戶，並同樣等到它的完整影像或確認不會收完。

#### Scenario: 影像收完
- **WHEN** 某帳戶這輪 `accountDownloadEnd` 到達，且這輪沒有 `AccountReady=false`，也有無後綴的 `NetLiquidation`
- **THEN** `RM:IBKR:{account}:balance` 與 `RM:IBKR:{account}:summary` MUST 被寫入，且兩者 `updateTimeMs` 相同

#### Scenario: 帳戶未就緒
- **WHEN** 這輪出現 `AccountReady=false`，其後仍收到 `accountDownloadEnd`
- **THEN** 兩個 `RM:IBKR` 鍵 MUST 保持上一份

#### Scenario: 輪詢未結束不再開一輪
- **WHEN** 30 秒到期但上一輪帳戶更新仍在等待影像
- **THEN** 系統 MUST NOT 再向 IB 發出另一輪帳戶更新訂閱

### Requirement: 持倉 key
pair 組得出來、主上市地有值且不是 `SMART` 時，key MUST 是 `{primaryExch}:{secType}:{pair}`。pair 組得出來、主上市地空或是 `SMART`、合約交易所有值且不是 `SMART` 時，key MUST 是 `{exchange}:{secType}:{pair}`。pair 組得出來但兩段都沒有可用值，且 `conId` 不是 0 時，key MUST 是 `{secType}:conid:{conId}`。pair 組不出來且 `conId` 不是 0 時，key MUST 是 `{venue}:{secType}:conid:{conId}`，`venue` 用主上市地，沒有就用合約交易所；兩邊都沒有時 MUST 改成 `{secType}:conid:{conId}`。`conId` 也是 0 時，這一列 MUST NOT 寫入，其餘現金與持倉照常更新。規格外的 `secType` MUST 仍進持倉。

#### Scenario: 主上市地是空的
- **WHEN** 持倉的 pair 可以組成，主上市地是空的，合約交易所是 `HKFE`
- **THEN** 該列的 key MUST 以 `HKFE:` 開頭，且 MUST NOT 含空的第一段
