## Why

當 IBKR ScaleTrader 的開倉組件訂單（Component Orders）在盤口撮合成交後，IB 伺服器端演算法會自動派生出相反方向的止盈訂單（Profit Taking Orders）。在 TWS API 回調中，這些止盈子單會自動繼承母單的 `orderRef`（如 `api_scale_1792635490740`）。

目前網關以 `orderRef`（即 `client_oid` / `omsId`）作為 Redis OMS 哈希表（`URANUS:...:O:...`）、風控字典（`RM:IBKR:...:open`）及內部記憶體索引（`OrderCache._orderByOMSId`）的唯一主鍵。這導致子單到達時會直接覆蓋母單狀態，使外部策略無法獨立查詢母單的成交進度，亦無法單獨撤回特定子單。此外，同一個母單在不同成交批次或檔位可能先後產生多個止盈子單，若僅使用固定後綴仍會造成子單間的覆蓋。

因此，需要建立基於子單全局唯一 `permId` 的標識符派生機制（`parentOrderRef@childPermId`），並在 OMS JSON 中完整暴露母子從屬關係。

## What Changes

- 在 `src/main/java/com/avalok/ib/IBOrder.java` 中建立子單識別與標識符標準化邏輯：
  - 依據 `order.parentId() != 0 || order.parentPermId() != 0` 判定為衍生子單。
  - 保留原始 `parentOrderRef`，並將子單的 `orderRef` 規範化為 `${parentOrderRef}@${childPermId}`（如 `api_scale_1792635490740@812216931`）。
  - 在 `toOMSJSON()` 中輸出規範化後的 `orderRef`、`client_oid` 與 `i`。
  - 在 `toOMSJSON()` 中針對子單顯式增加父子關聯欄位：`parentId`（母單 orderId）、`parentPermId`（母單 permId）及 `parentOrderRef`（母單原始 orderRef）。
- 在 `src/main/java/com/avalok/ib/handler/AllOrderHandler.java` 中確保交易回報與風控通道同步：
  - 當子單觸發成交明細（`execDetails`）時，TradeReport 中的 `orderRef` 同步映射為帶有 `@childPermId` 的獨立鍵位。
  - 風控通道 `RmOrderBuilder` 透過標準化後的 `orderRef` 自動獲取獨立鍵位，避免覆蓋母單風控記錄。

## Capabilities

### New Capabilities

- 無。

### Modified Capabilities

- `scaletrader-order`: 擴充 ScaleTrader 訂單狀態管理，支援多止盈子單的唯一鍵位派生（`${parentOrderRef}@${childPermId}`）及母子訂單層級關聯欄位輸出。

## Impact

- `src/main/java/com/avalok/ib/IBOrder.java`
- `src/main/java/com/avalok/ib/handler/AllOrderHandler.java`
- 下游影響：
  - Redis 哈希表 `URANUS:...:O:...` 中母單與各子單並存，可使用原 `orderRef` 查母單，使用 `orderRef@childPermId` 查特定止盈子單。
  - 訂單撤銷：可透過傳入 `api_scale_...@childPermId` 精準單獨撤銷特定止盈單，或傳入母單 `orderRef` 撤銷母單及關聯子單。
  - 完全向下相容：普通無父單關係的常規委託完全不受影響。
