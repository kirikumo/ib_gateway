## 1. 訂單模型與參數修改擴充 (IBOrder.java)

- [x] 1.1 在 `IBOrder.java` 中實現 `setParams(JSONObject changeInfo)` 方法，完整支援限價 `p`、數量 `s` 以及 ScaleTrader 的 11 個原生參數設定。驗證：編寫單元測試傳入不同欄位的 `changeInfo`，斷言底層 `Order` 物件各屬性正確更新。
- [x] 1.2 確保修改後的 `IBOrder` 序列化至 OMS JSON 時，正確反映修改後的 ScaleTrader 欄位值。驗證：單元測試呼叫 `toOMSJSON()` 確認新增或修改的數值完整呈現。

## 2. 網關命令轉發與快取維護 (GatewayController.java)

- [x] 2.1 在 `GatewayController.java` 的 `editOrder` 中接入 `ibOrder.setParams(changeInfo)`。驗證：確認呼叫 `placeOrder(ibOrder)` 時保留既有 `orderId`，向 IBKR 正確送出修改請求。
- [x] 2.2 確保全量單元測試通過。驗證：執行 `mvn test` 全部測試通過，無任何編譯錯誤或回歸。

## 3. 沙盒帳戶 (DUQ453614) 實測驗證

- [x] 3.1 重新編譯並打包 `target/ibgateway-3.1.jar`。驗證：`mvn package -DskipTests` 建置成功。
- [x] 3.2 使用帳戶 `DUQ453614` 上的既有 ScaleTrader 訂單 `api_scale_7705_restart_1793865189722` 測試修改最大持倉 `s`。驗證：透過 Redis 發布 `EDIT_ORDER` 指令，觀察 Redis OMS 中 `ttl_qty` 與 `remained_qty` 正確更新，`permId` 保持不變。
- [x] 3.3 測試修改止盈偏移 `scaleProfitOffset` (PT)。驗證：透過 Redis 發布 `EDIT_ORDER` 將 PT 由 6.0 改為 8.0 / 10.0，觀察 Redis OMS 中 `scaleProfitOffset` 成功更新。
