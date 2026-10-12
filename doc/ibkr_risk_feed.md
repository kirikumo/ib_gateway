# IBKR gateway：Redis 寫入要求

本文件只定義 IBKR gateway 要寫的新鍵。既有鍵照現在的方式繼續寫，不在這裡改格式。

數字是 JSON number，不是字串。時間是 UTC Unix 毫秒整數。除了本文件寫明可以是 JSON `null` 的欄位，規定欄位不可省略，也不可用空字串。

委託與連線是即時鍵。餘額與摘要是慢速快照，不是秒級資料。

## 鍵

`{account}` 是該 IB 帳戶編號。`{gateway}` 是這個 gateway 進程的名稱。

| 用途 | 鍵 | Redis 型別 | 速度 |
|---|---|---|---|
| 連線 | `RM:IBKR:{gateway}:connection` | STRING，一個 JSON。每 5 秒覆寫，TTL 15 秒 | 即時 |
| 未完成單 | `RM:IBKR:{account}:open` | HASH。field 是 `orderRef`，value 是訂單 JSON 字串 | 即時 |
| 訂單推送 | `RM:IBKR:{account}:order` | PUB/SUB。訊息是一個 JSON 物件 | 即時 |
| 餘額 | `RM:IBKR:{account}:balance` | STRING，一個 JSON | 慢速快照 |
| 摘要 | `RM:IBKR:{account}:summary` | STRING，一個 JSON | 慢速快照 |

即時鍵先寫 `open`，最後才 `PUBLISH`。推送訊息裡要有完整訂單欄位。

餘額與摘要沒有 TTL。連線鍵消失後，這兩份只代表斷線前最後一輪。

## 連線

```json
{ "updateTimeMs": 1760000000789 }
```

| 欄位 | 要求 | 直接可用 |
|---|---|---|
| 鍵 | 只在與 IB 的工作階段還在時覆寫。每 5 秒一次，TTL 15 秒 | 鍵在 = 這次心跳時工作階段仍在。鍵消失 = 超過 15 秒沒覆寫 |
| `updateTimeMs` | 必有 | 這次心跳的 Unix 毫秒 |

進程還在、IB 工作階段已經斷了，就停止覆寫，讓鍵自己過期。

## 未完成單

`open` 只保留 `status` 為 `new` 或 `cancelling` 的單。`filled`、`cancelled`、`rejected`、`removed` 從 hash 刪除，並推到 `RM:IBKR:{account}:order`。連線期間先刪再推。重連對帳時先推、再刪。

未完成單滿足 `remainedQty = qty - filledQty`。`filled`、`cancelled`、`rejected` 的 `remainedQty` 是 `0`，`qty` 仍是原始委託數量。部分成交後撤銷時，`qty - filledQty` 可以大於 `0`。`removed` 沿用刪除前的 `remainedQty`。

同一 `orderRef` 的 `updateTimeMs` 必須嚴格變大。同一毫秒有兩筆時，後一筆寫成上一筆加 1。

未完成單數是 `open` 的 field 數。餘額裡不放 `openCount`。

| IB 狀態 | 寫入 `status` | `open` |
|---|---|---|
| `ApiPending`、`PendingSubmit`、`PreSubmitted`、`Submitted`、`Inactive`、`Unknown` | `new` | 保留 |
| `PendingCancel`、`PreCancelled` | `cancelling` | 保留 |
| `Filled` | `filled` | 刪除並推送 |
| `Rejected` | `rejected` | 刪除並推送 |
| `Cancelled`、`Canceled`、`ApiCancelled` | `cancelled` | 刪除並推送 |

`Inactive` 與 `Unknown` 留在 `open`。`PreCancelled` 仍可能成交，不寫成 `cancelled`。`WarnState` 不寫成 `cancelled` 或 `rejected`。

重連後，未完成單與當日完結單都到齊，再處理仍留在 `open`、但新的未完成單快照裡已經沒有的 `orderRef`：

| 對得上什麼 | 推什麼 |
|---|---|
| 當日完結單，狀態是 `Filled` | `filled`。`filledQty` 用完結單上的成交量；沒有就用當日成交回報的累計量；再沒有就沿用刪除前的 `filledQty`。`avgPrice` 用成交回報的均價；沒有就寫 `null`，不寫 `0`。`remainedQty` 為 `0` |
| 當日完結單，狀態是 `Cancelled` 或 `ApiCancelled` | `cancelled`。包含零成交撤銷。`remainedQty` 為 `0`。`qty` 與 `filledQty` 沿用刪除前那筆；完結單有給成交量就用完結單的 |
| 當日完結單，狀態是 `Rejected` | `rejected`。`remainedQty` 為 `0`。`qty` 與 `filledQty` 沿用刪除前那筆；完結單有給成交量就用完結單的 |
| 對不上完結單，也對不上當日成交回報 | `removed`。帶上刪除前最後一次的 `qty`、`filledQty`、`price`、`side`、`currency`、`orderType`、`secType`、`pair`、`exchange`、`multiplier`。當時是 `null` 就仍是 `null` |

`updateTimeMs` 大於該 `orderRef` 的上一筆。`removed` 只在這次重連對帳出現。完結單只涵蓋當日，且包含已成交、已拒絕、使用者撤銷（含零成交撤銷）。`apiOnly` 用 `false`，以便包含 TWS 送出的單。成交回報只涵蓋當日午夜之後。跨日斷線，或當日完結單與當日成交回報都對不上時，推 `removed`。`removed` 的 `filledQty` 是斷線前最後看到的量。

## 訂單

```json
{
  "orderRef": "tws:123456789",
  "updateTimeMs": 1760000000456,
  "account": "DU123456",
  "status": "new",
  "side": "BUY",
  "orderType": "LMT",
  "price": 350.2,
  "qty": 1000,
  "filledQty": 200,
  "remainedQty": 800,
  "avgPrice": 350.1,
  "currency": "HKD",
  "commission": 12.5,
  "commissionCurrency": "HKD",
  "secType": "STK",
  "pair": "HKD-700",
  "exchange": "SEHK",
  "multiplier": 1
}
```

| 欄位 | 要求 | 直接可用 |
|---|---|---|
| `orderRef` | 同一張 IB 單永遠相同 | 下單請求有 orderRef 就原樣寫回。經這座閘道下的單，這個字串必須是 `uranus_` 或 `api_` 開頭，否則下單失敗。沒有 orderRef 的手動單寫 `tws:` 加十進位 permId。permId 仍是 `0` 時不寫、不推 |
| `updateTimeMs` | 嚴格變大 | Unix 毫秒。同一毫秒則上一筆加 1 |
| `account` | 必有 | 與這張單的 IB 帳戶編號相同 |
| `status` | `new`、`cancelling`、`filled`、`cancelled`、`rejected`、`removed` | 連線期間按狀態表。`removed` 只在重連對帳 |
| `side` | `BUY` 或 `SELL` | 委託方向。成交回報的 `BOT` 寫 `BUY`，`SLD` 寫 `SELL`。`SSHORT` 寫 `SELL` |
| `orderType` | 大寫 | 見下方價格。`LMT`、`MKT`、`STP`、`STP LMT` 以外的類型用 IB 大寫原文，例如 `TRAIL`、`MIT`、`MOC`、`LOC` |
| `price` | 數字或 `null` | 見下方價格。沒有可用價格時寫 `null`，不寫 `0`。組合單淨價可以是負數 |
| `qty` | 數字 | 原始委託數量。保留 IB 的小數。組合單是組數 |
| `filledQty` | 數字 | 已成交數量。沒有成交寫 `0` |
| `remainedQty` | 數字 | 未完成單等於 `qty - filledQty`。`filled`、`cancelled`、`rejected` 為 `0` |
| `avgPrice` | 數字或 `null` | 成交均價，幣別是 `currency`。沒有成交寫 `0`。重連對上 `Filled` 但沒有均價時寫 `null`，不寫 `0` |
| `currency` | 三碼大寫 | 見下方。股票是報價幣 |
| `commission` | 數字 | 已發生佣金合計。還沒收到佣金回報寫 `0`。兩筆回報幣別不同時不加總，`commissionCurrency` 留第一個非空幣別 |
| `commissionCurrency` | 三碼大寫或沿用上一筆 | 佣金回報的幣別。回報沒給幣別就與 `currency` 相同 |
| `secType` | 大寫 | 現稿類型用 `STK`、`OPT`、`FUT`、`FOP`、`CASH`、`BOND`。其餘用 IB 大寫原文，例如 `WAR`、`CFD`、`CRYPTO`、`FUND`、`CONTFUT`。組合單父單是 `BAG` |
| `pair` | 字串或 `null` | 見下方。組不出就寫 `null` |
| `exchange` | 大寫或 `null` | 主上市地。還沒知道，或目前只是 `SMART`，都寫 `null`。期貨沒有主上市地時用合約交易所，如 `HKFE` |
| `multiplier` | 數字或 `null` | 與 pair 裡的乘數相同。股票沒給就寫 `1`。其他類型沒給就寫 `null`。美股期權查到後是 `100` |
| `comboLegs` | 陣列，單腿省略 | 見下方。沒有腿的單不要這個欄位 |

明細未齊也寫、也推。股票與 `CASH` 的 `pair` 能組成 `{currency}-{symbol}` 就寫，例如 `HKD-700`，不用等主上市地。主上市地還沒到，或目前只是 `SMART`，`exchange` 寫 `null`。期貨、期權的最後交易日還不是 8 碼 `lastTradeDate` 時，`pair` 寫 `null`。日期用 `lastTradeDate`，不用到期日 `realExpirationDate`。補齊後同一個 `orderRef` 再推一筆，`updateTimeMs` 更大。`open` 的 field 始終是 `orderRef`。委託不抄進持倉。持倉來自這一輪帳戶快照；部位已經在 IB 持倉裡的，仍按持倉 key 寫入。

佣金常常晚於 `Filled`。終態那一拍 `commission` 可以是 `0`。之後同一 `orderRef` 可以再推一筆：人已不在 `open`，`commission` 改為合計，`updateTimeMs` 更大。沒有後續回報就維持 `0`。

### 價格

| `orderType` | `price` |
|---|---|
| `LMT` | 限價 `lmtPrice` |
| `MKT` | `null`。閘道不填參考價 |
| `TRAIL` | `null`。不把 `auxPrice`、`trailingPercent`、`trailStopPrice` 寫進 `price` |
| `TRAIL LIMIT` | `null`。不把 `lmtPrice`、`lmtPriceOffset`、`auxPrice`、`trailStopPrice` 寫進 `price` |
| `STP` | 觸發價 |
| `STP LMT` | 限價 `lmtPrice`。沒有限價則 `null` |
| `LOC` 等帶限價的其他類型 | 限價。沒有限價則 `null` |
| 其餘沒有對應價格規則的類型 | `null` |
| `BAG` | 有整組淨限價就寫淨限價，可以是負數。沒有才 `null` |

`null` 表示沒有可以用來乘數量的價格。`TRAIL` 與 `TRAIL LIMIT` 各寫 IB 原文，不併成同一個字。回呼裡未設定的 `0` 仍寫 `null`。

### 組合單

`BAG` 寫進 `open` 並推送。父單一個 `orderRef`，另加 `comboLegs`。成交後的持倉按腿拆開，餘額裡不另加一列 `BAG`。

| 欄位 | 寫法 |
|---|---|
| 父單 `price` | 整組淨限價。沒有才 `null` |
| 父單 `qty` | 組數 |
| 腿 `pair` | 該腿明細。未齊寫 `null` |
| 腿 `side` | `BUY` 或 `SELL`。`SSHORT` 寫 `SELL` |
| 腿 `qty` | 父單數量 × 該腿比例 |
| 腿 `price` | IB 的單腿價格。沒給就 `null` |
| 腿 `exchange` | 主上市地。`SMART` 或未齊寫 `null` |
| 腿 `secType`、`multiplier` | 與單腿同一套。沒給時股票乘數寫 `1`，其他類型寫 `null` |
| 腿 `conId` | IB 給的 `conId`。沒有就省略這一格 |

組合單的數量級用父單淨價，不用各腿價格相加。每一腿的 `multiplier` 都是數字且彼此相等，且父單 `price` 不是 `null` 時，數量級是 `abs(父單 price) × remainedQty × 該乘數`。部分成交後用剩餘組數，不用原始組數。任一腿乘數仍是 `null`，或各腿乘數不相同（例如正股 `1`、期權 `100`），或父單 `price` 為 `null`，這一拍沒有數量級。淨價為負表示這筆是收錢。

### 股票與外匯的金額

股票買賣的 `currency` 都是報價幣。數量級是 `price * remainedQty * multiplier`。`price` 為 `null` 時這條不算。

外匯（`secType` 為 `CASH`）的數量單位是標的幣（pair 右邊）。

| 方向 | `currency` | 數量級 |
|---|---|---|
| 買入 | pair 左邊，如 `HKD-USD` 的 `HKD` | `price * remainedQty * multiplier` |
| 賣出 | pair 右邊，如 `USD` | `remainedQty` |

賣出外匯不要用 `price * remainedQty`。`pair` 仍是 `null` 時，這一拍分不出左右，賣出外匯沒有數量級。

### pair

| secType | 格式 | 範例 |
|---|---|---|
| `STK`、`CASH` | `{currency}-{symbol}` | `USD-AAPL`、`HKD-700`、`HKD-USD` |
| `FUT` | `{currency}-{symbol}@{yyyyMMdd}@{multiplier}` | `HKD-HSI@20260728@50` |
| `OPT`、`FOP` | `{currency}-{symbol}@{yyyyMMdd}@{multiplier}{C或P}{strike}@{conId}` | `USD-AAPL@20260320@100C150.0@12345678` |
| `BOND` | `{tradingClass}-{conId}` | `IBOND-498975542` |

`yyyyMMdd` 是 `lastTradeDate`，不是到期日 `realExpirationDate`。還沒有 8 碼時，期貨與期權的 `pair` 寫 `null`。`strike` 固定一位小數。`C` 是 Call，`P` 是 Put。期貨與期權的 `@乘數` 一定要寫，即使乘數是 `1`。股票與 `CASH` 的 pair 不用主上市地。

## 餘額（慢速快照）

IB 同時只維持一個帳戶的帳戶更新訂閱。再訂另一個帳戶時，前一個被取消。每次開始訂閱會先收到全量，以 `accountDownloadEnd` 結束；只有收完才寫餘額和摘要。初始回呼之後，部位變化會再推，帳戶數值有變動時最多每 3 分鐘一次，頻率不能調。多個帳戶要輪流拿完整影像時，先停止上一個訂閱，再開始下一個，並各自等到 `accountDownloadEnd`。間隔是這件事花的時間，不是固定的 30 秒或 `N × 3` 秒。要在 3 分鐘以內再拿一份全量，先停止訂閱再重新開始，並等到 `accountDownloadEnd`。這一輪沒有 `accountDownloadEnd`，或出現 `AccountReady=false`，餘額與摘要都保持上一份，`updateTimeMs` 不動。成交後的現金與張數要等到該帳戶下一份完整快照才進這份 JSON。`updateTimeMs` 是寫出這份完整快照的時間。時間戳沒動，數字就是舊的。

```json
{
  "updateTimeMs": 1760000000123,
  "cash": {
    "USD": { "balance": 58876.19 }
  },
  "positions": {
    "NASDAQ:STK:USD-AAPL": {
      "pos": 180,
      "marketValue": 2336.4,
      "marketPrice": 12.98,
      "secType": "STK",
      "currency": "USD",
      "pair": "USD-AAPL",
      "multiplier": 1
    }
  }
}
```

沒有現金時 `cash` 為 `{}`。沒有持倉時 `positions` 為 `{}`。張數為 0 的列不寫。

| 欄位 | 要求 | 直接可用 |
|---|---|---|
| `updateTimeMs` | 每次 `SET` 都有 | 寫出這份完整快照的 Unix 毫秒 |
| `cash.*.balance` | 數字 | 該幣別 `CashBalance`。成交當下承認的現金加期貨盈虧。未成交買單不減少這個數。不是 AvailableFunds，不是 NetLiquidation，不用 `TotalCashBalance` |
| `positions` 的 key | 見下表 | `pair` 組得出來用原 key。組不出來用 `conId`，避免空 `pair` 撞車 |
| `pos` | 數字，可負，且非 0 | 持倉數量。未扣未成交賣單 |
| `marketValue` | 數字或 `null` | 市值，單位是 `currency`。沒有市價時 `null`，不要寫 `0` |
| `marketPrice` | 數字或 `null` | 這一輪快照的單價。沒有時 `null`。不是逐筆行情 |
| `secType`、`currency`、`pair`、`multiplier` | 與訂單相同 | 持倉與委託同一套。`pair` 組不出時 JSON 裡的 `pair` 仍是 `null` |

| 條件 | key |
|---|---|
| pair 組得出來，主上市地有值且不是 `SMART` | `{primaryExch}:{secType}:{pair}` |
| pair 組得出來，主上市地空或是 `SMART`，合約交易所有值且不是 `SMART` | `{exchange}:{secType}:{pair}` |
| pair 組得出來，但上面兩段都沒有可用值，且 `conId` 不是 0 | `{secType}:conid:{conId}` |
| pair 組不出來，且 `conId` 不是 0 | `{venue}:{secType}:conid:{conId}`。`venue` 用主上市地；沒有就用合約交易所。兩邊都沒有時改成 `{secType}:conid:{conId}` |
| `conId` 也是 0 | 這一列持倉不寫。其餘現金與持倉照常更新 |

規格外的 `secType` 也進持倉。組不出市值時 `marketPrice` 與 `marketValue` 為 `null`，不寫 `0`。這份 JSON 沒有 `openCount`。委託不因為寫進 `open` 就多一列持倉。

## 摘要（慢速快照）

與餘額同一輪寫入。這一輪沒有 `accountDownloadEnd`，或出現 `AccountReady=false`，摘要與餘額一起保持上一份。不走另一次帳戶摘要。

```json
{
  "updateTimeMs": 1760000000123,
  "netLiquidation": 1000000.5,
  "baseCurrency": "HKD"
}
```

| 欄位 | 要求 | 直接可用 |
|---|---|---|
| `updateTimeMs` | 必有 | 寫出這份完整快照的 Unix 毫秒 |
| `netLiquidation` | 數字 | `NetLiquidation` 的數值，單位是 `baseCurrency` |
| `baseCurrency` | 三碼大寫 | `NetLiquidation` 那一列自己的 currency。不是一個名叫 `BaseCurrency` 的欄位，也不要假設是 `USD` |
