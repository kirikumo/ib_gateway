# Tasks

## 1. IBOrder 對外鍵

- [x] 1.1 刪除 `normalizeChildOrderRef()` 以及建構子、`setStatus()` 對它的呼叫。子單對外鍵改在 `orderRef()`、`omsClientOID()`、`toOMSJSON()` 讀取時計算：`permId` 大於 0 且 `orderRef` 非空時為 `母單orderRef@permId`，已有該後綴則不再加；`permId` 仍是 0 時 `omsId` 為空。IB 物件上的 `orderRef` 不改。驗證：用 Java 17 以上執行 `mvn -Dtest=IBOrderChildOrderTest test`，母單與子單的 IB 字串不變，子單的 `orderRef`、`client_oid`、`i` 為 `api_scale_1792635490740@812216931`，呼叫兩次結果相同，`permId` 為 0 的子單不佔母單鍵。

## 2. 風控鍵與文件

- [x] 2.1 在 `RmOrderBuilder.orderRef()` 加上與 `IBOrder` 相同的子單判斷，不引用 `IBOrder`。驗證：用 Java 17 以上執行 `mvn -Dtest=RmOrderBuilderTest test`，母單維持原 `orderRef`，子單為 `母單orderRef@permId`，`permId` 為 0 時結果是 `null`。
- [x] 2.2 在 `doc/ibkr_risk_feed.md` 訂單身份那一列補上：券商生成且 `orderRef` 與母單相同的子單，對外鍵是 `母單orderRef@permId`。其餘句子不動。驗證：該列讀得到這句，原本的下單原樣寫回敘述仍在。

## 3. 成交請求順序

- [x] 3.1 刪除 `PendingTrades`。`tradeReport` 找到快取或處理中的訂單就用對外鍵；否則用成交上的 `orderRef`。不掛起，不另造鍵。驗證：`IBOrderChildOrderTest` 不再引用 `PendingTrades`，既有子單鍵測試仍過。
- [x] 3.2 `_postConnected` 不先 `reqExecutions`。未完成單與當日完結單都結束後，`AllOrderHandler` 呼叫 `GatewayController.requestExecutions()` 一次。`resetStatus()` 清掉已要求的標記。OMS 仍在 `openOrderEnd` 初始化，不等成交。
- [x] 3.3 `RmOrderFeed` 對帳等未完成單結束、當日完結單結束、以及 `tradeReportEnd`。`beginReconnect()` 清掉成交結束標記。兩個訂單結束還不出現 `removed`，成交結束後才出現。驗證：用 Java 17 以上執行 `mvn -Dtest=RmOrderFeedTest test`。
- [x] 3.4 確認一起通過。驗證：用 Java 17 以上執行 `mvn -Dtest=IBOrderChildOrderTest,RmOrderBuilderTest,RmOrderFeedTest test`，結果為 BUILD SUCCESS。
