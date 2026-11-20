## Why

在 ScaleTrader（網格／階梯演算法）策略實際運作時，交易員或外部量化策略經常需要動態調整運行中母單的各項參數，典型場景包括：

1. **動態調整最大持倉（`totalQuantity` / `s`）**：
   - 當市場流動性變化或風控要求降槓桿時，策略需要將最大持倉下調至當前已成交量（`executed_qty`），使演算法立即閉鎖並停止繼續在盤口向下／向上掛出新的開倉組件（即「軟鎖倉」機制），同時維持現有止盈單繼續有效；
   - 當需要擴大網格規模或恢復開倉時，策略需要調大 `totalQuantity`，使演算法自動在低位／高位恢復掛單。
2. **動態調整止盈利潤偏移（`scaleProfitOffset` / PT）**：
   - 隨市場波動率（ATR）變化，策略需要動態放大（如從 6.0 點調高至 8.0 或 10.0 點）或縮小止盈利潤空間，以適應不同市況。
3. **動態調整價格步長（`scalePriceIncrement`）或重設旗標（`scaleAutoReset`）** 等演算法核心參數。

目前網關的程式碼現狀：
- `GatewayController.editOrder(String omsId, JSONObject changeInfo)` 目前僅提取了限價 `p` 與數量 `s`，其餘所有 ScaleTrader 參數均未解析，導致外部傳入的 `scaleProfitOffset` 等欄位被直接忽略，無法送達 IBKR 底層。
- 另外，若策略透過 `PLACE_ORDER` 指令帶入既有訂單標識（`orderRef` 或 `i`/`permId`）嘗試改單，由於 `new IBOrder(JSONObject)` 未繼承原有的內部 `orderId`，底層 `ApiController` 會將其視為新單並重新生成 `orderId`，引發重複下單或訂單衝突。

因此，需要在網關中完整實現 ScaleTrader 訂單的動態修改能力，支援透過 `EDIT_ORDER` 指令就地修改 ScaleTrader 的各項參數，保持 `orderId` 與 `permId` 不變，並在測試帳戶 `DUQ453614` 上以運行中的訂單 `api_scale_7705_restart_1793865189722` 進行實盤聯調驗證。

## What Changes

- 在 `src/main/java/com/avalok/ib/IBOrder.java` 中新增 `applyChangeInfo(JSONObject changeInfo)` 方法：
  - 支援更新價格 `p`（`lmtPrice`）與數量 `s`（`totalQuantity`）。
  - 完整解析並更新 11 個 ScaleTrader 原生參數（`scaleProfitOffset`、`scaleInitLevelSize`、`scaleSubsLevelSize`、`scalePriceIncrement`、`scalePriceAdjustValue`、`scalePriceAdjustInterval`、`scaleAutoReset`、`scaleInitPosition`、`scaleInitFillQty`、`scaleRandomPercent`、`scaleTable`）。
- 在 `src/main/java/com/avalok/ib/GatewayController.java` 中：
  - 更新 `editOrder` 方法，調用 `ibOrder.applyChangeInfo(changeInfo)` 進行就地更新，並呼叫 `placeOrder(ibOrder)` 送出修改請求。
  - 在 `PLACE_ORDER` 處理邏輯中，若傳入已存在的 `orderRef` 或 `i`/`permId`，優先關聯快取中既有訂單的 `orderId`，避免誤判為全新委託。
- 在 `src/test/java/com/avalok/ib/handler/IBOrderChildOrderTest.java` 中新增單元測試，驗證 `applyChangeInfo` 對各項 ScaleTrader 參數與 `toOMSJSON()` 序列化的正確性。
- 提供實盤／沙盒驗證方案，使用帳戶 `DUQ453614` 既有的 `api_scale_7705_restart_1793865189722` 測試 PT 與最大持倉的修改。

## Capabilities

### New Capabilities

- 無。

### Modified Capabilities

- `scaletrader-order`: 擴充 ScaleTrader 訂單動態修改能力，支援透過 `EDIT_ORDER`（及 `placeOrder`）動態調整 `scaleProfitOffset`（PT）、最大持倉量 `s`（`totalQuantity`）及各項 Scale 演算法參數，並維持原 `orderId` 與 `permId` 唯一性。

## Impact

- `src/main/java/com/avalok/ib/IBOrder.java`
- `src/main/java/com/avalok/ib/GatewayController.java`
- 下游影響：
  - 外部量化策略可透過 Redis 發布 `EDIT_ORDER` 即時調整 ScaleTrader 的 PT 偏移或最大持倉，無需經歷「撤銷母單 -> 撤銷孤兒子單 -> 帶底倉重下」的高風險繁瑣流程。
  - Redis OMS 哈希表（`URANUS:...:O:...`）與 `O_channel` 推播將即時反映修改後的數值。
