## Why

閘道把合約數量規則寫進 Redis 時用 `Decimal.longValue()`，小於 1 的值會變成 `0`。下游用 `IBGateway:Contract:...` 的 `minSize` / `sizeIncrement` 判斷下單步進時會誤判（例如 IDEALPRO `USD.HKD` 顯示 `minSize: 0`，無法分辨「未填」與「被截斷」）。外匯小數張被 IB 以 10318 拒絕後，這份失真快取會讓人以為合約允許任意小數。

同一組欄位裡，`suggestedSizeIncrement` 是 IB 的下單建議步進。舊的行情 lot 倍率 `mdSizeMultiplier` 已廢除後，閘道把它誤當成 top／depth 張數乘數。TWS 985 之後美股 `tickSize` 已是股數；再乘這個欄位會把非港股盤口放大或在 `0`／invalid 時走錯路徑。

## What Changes

- `FIND_CONTRACTS` 與 `FIND_CONTRACTS_TO_REDIS` 寫出的合約 JSON 中，`minSize`、`sizeIncrement`、`suggestedSizeIncrement` 改存 IB 回傳的原值（可為小數），語意對齊既有的 `minTick`。
- IB 未填或 invalid 的 size 欄位寫 `null`，不再寫成 `0` 或 `Long.MAX_VALUE`。
- 訂閱與重訂 top／depth 時，**停止**用 `suggestedSizeIncrement` 當行情張數倍率。對外發布的 `s` 對齊期權與 SEHK：以 IB 回傳張數為準（非 SEHK 仍可乘合約 `multiplier`）。Redis 仍寫 `suggestedSizeIncrement` 原值，只當數量規則，不當倍率。
- **BREAKING**（Redis 合約 JSON）：size 三欄可能從整數變成小數或 `null`。只讀 `GET IBGateway:Contract:...` 的客戶端若假設一定是整數，需要改為按數字／null 解析。
- **BREAKING**（行情張數）：非 SEHK／HKFE 的 top／depth，若舊倍率不是 `0` 或 `1`（例如 `100`），對外 `s` 會變。SEHK／HKFE 與期權路徑本來就不乘這個欄位，行為不變。
- 不改下單、`cashQty`、OMS 數量、10318 處理。

## Capabilities

### New Capabilities

- `contract-details-cache`：合約細節寫入 Redis 時，數量規則欄位必須保留 IB 原值與「未填」語意，不得用整數截斷冒充規則。
- `market-data-size`：對外 top／depth 張數不得用 `suggestedSizeIncrement` 縮放；該欄位只描述下單建議步進。

### Modified Capabilities

- （無既有 spec）

## Impact

- `src/main/java/com/avalok/ib/handler/ContractDetailsHandler.java`（`writeDetail`、`detailToJSONObject`）
- `src/main/java/com/avalok/ib/GatewayController.java`（訂閱／重訂 top、depth 不再讀 `suggestedSizeIncrement` 當倍率）
- `src/main/java/com/avalok/ib/handler/TopMktDataHandler.java`、`DeepMktDataHandler.java`（張數計算不再依賴該欄位）
- Redis key：`IBGateway:Contract:{exchange}:{secType}:{pair}`（TTL 約 32 天；舊值要重查才更新）
- Redis key：`IBGateway:ReqIdContract:{id}`（`FIND_CONTRACTS_TO_REDIS`）
- 對外頻道：`URANUS:{exchange}:{pair}:{gw}:full_odbk_channel`、`full_tick_channel` 的 `s`
- 可選：`doc/ibkr_place_order_SOP.md` 註明 size 欄位可為小數或 null，且 `suggestedSizeIncrement` 不是行情倍率
- 無新依賴
