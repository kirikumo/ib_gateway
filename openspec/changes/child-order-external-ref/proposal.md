# Proposal

## Why

券商生成的 ScaleTrader 子單會抄母單的 `orderRef`。不加後綴時，母單與子單寫進同一把鍵。現在的實作把 `${parentOrderRef}@${childPermId}` 寫回 `Order.orderRef()`，改單會把這把合成鍵送回 IB。重連時又先要成交，訂單快照還沒到，成交就退回母單那把鍵。

## What Changes

- 子單對外鍵維持 `${parentOrderRef}@${childPermId}`。OMS、記憶體快取、`RM:IBKR:{account}:open` 與自動成交回報都用這把鍵。
- 後綴只在讀出對外鍵時計算。送給 IB 的 `Order.orderRef` 保持券商原來的字串。
- `permId` 還沒有時不寫出子單鍵，也不把這張子單記到母單鍵上。
- 重連先拉未完成單與當日完結單。兩個結束信號都到了，才要求當日成交一次。風控對帳等這次成交回報結束。OMS 初始化仍在未完成單結束時，不等成交。
- 已在快取或處理中的單，成交用該單的對外鍵。還沒有的單沿用成交上的 `orderRef`，不掛起，也不另造鍵。連線期間新長出的子單仍可能成交先到，這種情況接受與母單鍵相同。
- 母單的 `orderRef`、Scale 欄位解析、`EDIT_ORDER` 的改價改量，以及手動單既有的鍵，維持不變。

## Capabilities

### New Capabilities

- 無。

### Modified Capabilities

- `scaletrader-order`: 子單鍵只作為對外識別；不寫回送給 IB 的 `Order`。重連先收兩個訂單快照再要成交。快取裡已有的子單，成交用子單鍵。還沒有的單沿用成交上的 `orderRef`。

## Impact

- `src/main/java/com/avalok/ib/IBOrder.java`
- `src/main/java/com/avalok/ib/rm/RmOrderBuilder.java`
- `src/main/java/com/avalok/ib/rm/RmOrderFeed.java`
- `src/main/java/com/avalok/ib/handler/AllOrderHandler.java`
- `src/main/java/com/avalok/ib/GatewayController.java`
- `src/test/java/com/avalok/ib/handler/IBOrderChildOrderTest.java`
- `src/test/java/com/avalok/ib/rm/RmOrderBuilderTest.java`
- `src/test/java/com/avalok/ib/rm/RmOrderFeedTest.java`
- `doc/ibkr_risk_feed.md`：補一句券商生成且 `orderRef` 與母單相同的子單，對外鍵為 `母單orderRef@permId`
- 手動指令 `REQ_EXECUTIONS`（`TradeReportHandler`）不在這次範圍
