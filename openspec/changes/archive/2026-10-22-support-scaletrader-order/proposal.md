## Why

外部量化策略（如網格交易、VolCast 等）需要透過 Redis 協定控制 Interactive Brokers (IBKR) 的 ScaleTrader 演算法訂單（建立全新初始網格），以實現自動化分批進場建倉、每一檔位自動掛出獲利了結單（Profit Taking, PT）以及部位與網格重設（Auto Reset）。

目前 `ib_gateway` 僅支援常規委託欄位（如 lmtPrice, auxPrice, ocaGroup, hidden 等），缺少 TWS ScaleTrader 演算法的 11 個關鍵控制參數。這導致外部策略無法透過 API 下達 Scale 網格訂單，也無法在訂單狀態回報（`toOMSJSON()`）中獲取 ScaleTrader 的完整參數與狀態。

## What Changes

- 在 `src/main/java/com/avalok/ib/IBOrder.java` 的 JSON 建構子中，補全解析 TWS ScaleTrader 的 11 個核心參數並設定到底層 `com.ib.client.Order`：
  - `scaleInitLevelSize`: 初始檔位數量
  - `scaleSubsLevelSize`: 後續每一檔位數量
  - `scalePriceIncrement`: 網格每檔價格跳動間隔
  - `scalePriceAdjustValue`: 價格調整值
  - `scalePriceAdjustInterval`: 價格調整時間間隔（秒）
  - `scaleProfitOffset`: 自動利潤了結偏移量（Profit-Taking Offset）
  - `scaleAutoReset`: 是否自動重設網格（布林值）
  - `scaleInitPosition`: 初始建倉部位（用於重設時參考）
  - `scaleInitFillQty`: 初始已成交數量
  - `scaleRandomPercent`: 是否隨機化委託數量（布林值）
  - `scaleTable`: 進階階梯自訂網格字串
- 在 `src/main/java/com/avalok/ib/IBOrder.java` 的 `toOMSJSON()` 中，增加上述 11 個 ScaleTrader 欄位的序列化邏輯。當該欄位為有效設定值（非 `MAX_VALUE` 且非預設空值）時輸出至 JSON，確保下游 OMS 與風控通道能正確感知 ScaleTrader 狀態。
- 嚴格遵守「網關僅作為 IBKR 控制通道、與具體策略邏輯完全解耦」的原則，不引入策略私有欄位，完整還原 TWS 原生介面能力。
- 保持 Git 變更最小化，不修改 `src/main/java/com/ib/`。

## Capabilities

### New Capabilities

- `scaletrader-order`: 支援透過 Redis `PLACE_ORDER` 指令下達帶有 11 個 ScaleTrader 演算法原生參數的委託，並在訂單狀態回報（`toOMSJSON()` 及下游通道）中過濾並序列化生效的 ScaleTrader 欄位。

### Modified Capabilities

- 無。

## Impact

- `src/main/java/com/avalok/ib/IBOrder.java`：委託解析與序列化方法擴充。
- 下游讀者：`RM:IBKR:{account}:order`、`open` 與 OMS 頻道在收到 Scale 訂單時可讀取額外的 Scale 欄位；普通訂單回報保持原樣。
- **不修改** `src/main/java/com/ib/`。
- 相容性：完全向下相容，不影響所有既有訂單類型。
