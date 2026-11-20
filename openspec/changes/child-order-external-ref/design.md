# Design

## Context

見 `proposal.md` 的 Why。現況是 `IBOrder.normalizeChildOrderRef()` 在建構與 `setStatus()` 改寫 `Order.orderRef()`。`RmOrderBuilder.orderRef()` 讀的是這顆 IB 物件。`GatewayController.placeOrder()` 把同一顆物件送回 IB。`AllOrderHandler.tradeReport()` 在快取沒有該單時，使用成交上抄來的 `orderRef`。

重連時 `resetStatus()` 清空訂單快取。三個請求若一起發出，回呼會交錯，成交會在訂單進快取之前到達。

## Goals / Non-Goals

**Goals:**

- 對外鍵在讀取時計算，OMS 與風控用同一條規則。
- 子單在 `permId` 大於 0 且 `orderRef` 非空時，對外鍵為 `orderRef + "@" + permId`。已經以這個後綴結尾則不再加。
- 重連先收到未完成單與當日完結單兩個結束信號，再要求當日成交一次。風控對帳等這次成交回報結束。
- 已在快取或處理中的單，成交用對外鍵。還沒有的單沿用成交上的 `orderRef`，不掛起。

**Non-Goals:**

- 不改 Scale 欄位解析，不改 `EDIT_ORDER` 的改價與改量。
- 不改 `orderRef` 為空時的既有讀取結果，也不在這次新增任何鍵。
- 不改手動指令 `REQ_EXECUTIONS` 使用的 `TradeReportHandler`。
- 不把連線期間先到的成交留在記憶體裡等子單。
- `rm` 不依賴 `IBOrder`。

## Decisions

### 1. 對外鍵是純計算，兩處各寫一次

`IBOrder` 提供給 OMS 的 `orderRef()`、`omsClientOID()`、`toOMSJSON()` 使用同一計算。`RmOrderBuilder.orderRef()` 對 `com.ib.client.Order` 做同一條判斷：`parentId != 0` 或 `parentPermId != 0`，且 `permId` 大於 0，且 `orderRef` 非空。

子單的 `permId` 仍是 0 時，OMS 的 `omsId` 為空，不寫哈希、不佔母單鍵。風控這邊 `permId` 為 0 時本來就不寫。

刪除 `normalizeChildOrderRef()` 以及建構子、`setStatus()` 對它的呼叫。`isAlive()` 繼續看 IB 原字串。

**替代：** 繼續改寫 `Order.orderRef()`，讓風控自動跟著變。否決。`placeOrder()` 會把合成鍵送回 IB。

**替代：** 抽一個雙方共用的型別。否決。規則只有這幾個條件，多一個型別不會比兩處對齊更清楚。`RmOrderBuilder` 現在只依賴 IB 官方型別。

### 2. 兩個訂單快照結束後才要成交

`_postConnected` 只拉未完成單與當日完結單，不在那裡呼叫 `reqExecutions`。`openOrderEnd` 與 `completedOrdersEnd` 都到了之後，`AllOrderHandler` 呼叫 `GatewayController.requestExecutions()` 一次。`resetStatus()` 清掉這次已要求的標記，下次重連再要。OMS 仍在 `openOrderEnd` 初始化，不等成交。

`reqExecutions` 的 `tradeReportEnd` 呼叫 `RmOrderFeed.onExecutionsEnded()`。對帳要未完成單結束、當日完結單結束、以及這次成交回報結束三個信號都到。只到兩個訂單結束時，殘留的 `open` 還不能記成 `removed`。手動 `REQ_EXECUTIONS` 仍走 `TradeReportHandler`，不觸發對帳。

`tradeReport()` 先以 `permId` 找快取或目前處理中的訂單。找到就用該單的對外鍵。找不到就用成交上的 `orderRef`。不掛起，也不另造鍵。不用 `orderId`，那個會重用。

**替代：** 只把 `_postConnected` 裡三個請求的呼叫順序對調。否決。三個請求一起發出時，回呼仍會交錯。

**替代：** 成交先到就按 `permId` 掛起，等訂單進快取再寫。否決。重連改由請求順序避開。連線期間新子單仍可能成交先於 `openOrder`，這種沿用成交上的 `orderRef`。

### 3. 文件只補子單這一句

`doc/ibkr_risk_feed.md` 訂單身份那一列補上：券商生成、`orderRef` 與母單相同的子單，對外鍵是 `母單orderRef@permId`。其餘句子不動。

## Risks / Trade-offs

- [兩處規則以後不一致] → `IBOrderChildOrderTest` 與 `RmOrderBuilderTest` 各鎖一筆子單、一筆母單，以及子單 `permId` 為 0 時不產出母單鍵。
- [IB 已經存過帶後綴的 `orderRef`] → 該字串就是現在的券商原值。計算時若已以 `@permId` 結尾就不再加，送回 IB 時也不剝掉。
- [對帳若仍在兩個訂單結束時跑] → 成交表還是空的，殘留單會被記成 `removed`。對帳改等 `tradeReportEnd`。
- [新止盈子單的成交仍可能先於 `openOrder`] → 沿用成交上的 `orderRef`，不掛起。這把鍵與母單相同。

## Migration Plan

重啟後新進程依券商原字串計算對外鍵。先前已正確寫成 `母單orderRef@permId` 的 OMS 與 `open` 欄位會對上。曾被成交誤寫進母單鍵的內容，會在母單下一筆訂單回呼時被母單自己的狀態蓋過。不另做資料遷移。回退就是回到會改寫 `Order.orderRef()` 的版本。
