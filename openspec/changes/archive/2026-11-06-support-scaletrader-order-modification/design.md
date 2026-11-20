## Context

IBKR TWS API 的底層協定中，修改現有委託是透過 `placeOrder(int id, Contract contract, Order order)` 實現的。
當 `id` 與 IBKR 伺服器中已有的活躍訂單一致時，IBKR 引擎將該操作處理為「訂單修改」（Order Modification），而不是建立新委託。修改完成後，該訂單的 `orderId` 與全局 `permId` 完全保持不變，且既有的止盈子單關聯鏈條亦不會斷裂。

在當前 `ib_gateway` 實作中：
- `GatewayController.editOrder(String omsId, JSONObject changeInfo)` 方法在取得快取中的 `ibOrder` 物件後，僅檢查了 `changeInfo` 中的 `"p"` 與 `"s"`，任何 ScaleTrader 專屬參數均未傳遞給 `ibOrder.order`。
- 本提案旨在補全此處的欄位對齊，讓策略能夠安全、靈活地動態調整 ScaleTrader 的核心維度（包含 PT 與最大持倉）。

## Goals / Non-Goals

**Goals:**

- 支援 `EDIT_ORDER` 攜帶 ScaleTrader 參數（特別是 `scaleProfitOffset` (PT)、`s` (`totalQuantity` 最大持倉)、`scalePriceIncrement`、`scaleAutoReset` 等）進行安全修改。
- 修改過程必須嚴格維持原有的 `orderId` 與 `permId`，不得產生重複委託或破壞母子關聯。
- 修改後 IBKR 回調的最新狀態須即時更新至記憶體快取與 Redis OMS 哈希表（`URANUS:...:O:...`）。
- 在真實沙盒帳戶 `DUQ453614` 上，以目前處於 `Submitted` 狀態的 `api_scale_7705_restart_1793865189722` 進行實盤改單聯調測試，覆蓋 PT 與最大持倉的修改。

**Non-Goals:**

- 不修改 `com.ib` 目錄下的官方 SDK 原始碼。
- 不改變普通非 ScaleTrader 訂單的 `editOrder` 行為。
- 不在網關內自行計算修改後的限價或成交量，所有狀態變更均以 IBKR 官方推播為準。

## Decisions

### 1. 集中封裝 `IBOrder.applyChangeInfo`
- **決策**：在 `IBOrder` 類別中新增 `public void applyChangeInfo(JSONObject changeInfo)` 方法，將限價、數量以及所有 11 個 `scale*` 欄位的解析、型別轉換與指派集中於此。
- **理由**：
  - 高內聚：`IBOrder` 同時維護 `new IBOrder(JSONObject)` 初始解析與後續動態修改，避免在 `GatewayController` 堆砌過多的 JSON 提取邏輯。
  - 易測試：可在單元測試中直接測試不同 `changeInfo` 的更新效果，無須依賴完整的連線環境。

### 2. 就地更新與 `orderId` 傳遞
- **決策**：在 `GatewayController.editOrder` 中：
  ```java
  IBOrder ibOrder = orderCacheHandler.orderByOMSId(omsId);
  ...
  ibOrder.applyChangeInfo(changeInfo);
  placeOrder(ibOrder);
  ```
- **理由**：
  - 快取中的 `ibOrder` 已經持有有效的 `order.orderId()`。
  - 當調用 `placeOrder(ibOrder)` -> `_apiController.placeOrModifyOrder(order.contract, order.order, ...)` 時，由於 `order.orderId() != 0`，`ApiController` 不會再分配新 ID，直接將原 `orderId` 傳入底層 `m_client.placeOrder(contract, order)`，從而在 IBKR 端觸發精確的訂單修改。

### 3. 沙盒帳戶 `DUQ453614` 實測驗證方案
針對用戶指定的現有訂單 `api_scale_7705_restart_1793865189722`（CME MES 期貨，目前狀態 `Submitted`，已成交 4 手，原最大持倉 5 手，PT = 6.0）：
- **測試 1：修改最大持倉 `s`（`totalQuantity`）**
  - 操作：發送 `EDIT_ORDER`，帶 `omsId = "api_scale_7705_restart_1793865189722"`，`changeInfo = {"s": 6}`。
  - 驗證：Redis OMS 中該訂單的 `ttl_qty` 變為 6，`remained_qty` 變為 2；`permId` 保持 `608422602` 不變；TWS 盤口掛出下一檔組件。
  - 軟鎖倉測試：將 `s` 改為 4（當前已成交量），驗證 `ttl_qty = 4`，`remained_qty = 0`，盤口停止開倉，演算法進入閉鎖狀態。
- **测试 2：修改止盈偏移 `scaleProfitOffset`（PT）**
  - 操作：發送 `EDIT_ORDER`，`changeInfo = {"scaleProfitOffset": 8.0}`。
  - 驗證：Redis OMS 中該訂單的 `scaleProfitOffset` 更新為 8.0；`permId` 維持不變；確認既有止盈單或後續觸發的止盈價位符合預期。

## Risks / Trade-offs

- **訂單處於終態時的改單防護**：
  - 若訂單已經為 `Cancelled` 或 `Filled`，`editOrder` 必須保持現有的活躍狀態檢查（`!ibOrder.orderState.status().isActive()`），直接拒絕並返回錯誤，避免對已失效訂單發出無效請求。
- **IBKR 的數值合法性約束**：
  - IBKR 規定 ScaleTrader 的 `totalQuantity` 不能小於已經成交的手數。若外部傳入小於 `executed_qty` 的 `s`，IBKR 會返回報錯（如 code 201）。網關會透過現有的 `SingleOrderHandler` 接收該錯誤並廣播至 ACK 頻道，符合標準風控回報路徑。
