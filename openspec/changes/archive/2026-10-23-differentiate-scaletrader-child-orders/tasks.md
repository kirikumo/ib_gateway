## 1. 訂單模型與鍵位規範化 (IBOrder.java)

- [x] 1.1 在 `IBOrder` 中新增子單判定邏輯。驗證：當傳入 `parentId != 0` 或 `parentPermId != 0` 時，`isChildOrder()` 正確識別為子單。
- [x] 1.2 在 `IBOrder` 中實現基於 `${parentOrderRef}@${childPermId}` 的標識符規範化（防重複追加後綴）。驗證：單元測試驗證子單的 `omsClientOID()` 與 `order.orderRef()` 正確轉換且具備冪等性。
- [x] 1.3 在 `toOMSJSON()` 中輸出子單的 `parentId` 與 `parentPermId`。驗證：序列化後的 JSON 包含結構化父子關聯欄位（策略端由 `${parentOrderRef}@${childPermId}` 提取母單鍵位，無需冗餘輸出 `parentOrderRef`），且普通訂單不輸出這些欄位。

## 2. 訂單快取與下游協同 (AllOrderHandler.java)

- [x] 2.1 驗證 `OrderCache` 能夠同時以母單鍵位與子單鍵位獨立檢索各自的 `IBOrder`。驗證：母單與子單共存時，`byOMSId` 分別返回對應物件，無互相覆蓋。
- [x] 2.2 確保子單成交時 TradeReport 中的 `orderRef` 反映 `${parentOrderRef}@${childPermId}`。驗證：成交明細中 `orderRef` 與子單鍵位一致。

## 3. 整體驗收

- [x] 3.1 執行全量單元測試。驗證：`mvn test` 執行全部測試通過，BUILD SUCCESS。
- [x] 3.2 驗證常規訂單與風控模組無行為回歸。驗證：既有 `RmOrderFeedTest` 等全部單測通過。
