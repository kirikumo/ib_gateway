## Why

`SUB_TOP_MARK` 走到第一筆 `tickPrice` 時，javac 為 `switch (tickType)` 合成的 `TopMktDataHandler$4`（`$SwitchMap`）若載入失敗，會丟出 `NoClassDefFoundError`。IB 唯一的 inbound 執行緒只捕捉 `IOException`，執行緒一死 socket 仍顯示已連線：Redis 指令與 ACK 正常，但帳戶市值、帳戶摘要、行情 tick、訂單回調全部停擺，必須重啟 JVM 才能恢復。09/11 重連後同一個帳戶摘要 reqId `10001189` 活到 09/14，代表這是長跑 JVM 被單一 `Error` 打死，不是 TWS 每日重啟本身。

## What Changes

- IB `processMsgs` 執行緒遇到 handler / decoder 的 `RuntimeException` 或 `Error` 時記錄錯誤並繼續處理後續訊息，不再讓整條 inbound 停掉。
- `TopMktDataHandler` 與 `OptionTopMktDataHandler` 的 TickType 分派改為不產生 `$SwitchMap` 合成類的寫法（`==` / if-else），即時行情與延遲行情同源欄位行為保持不變。
- 不改變 Redis 指令語意：`FIND_ACCOUNT_SUMMARY` 在已訂閱時略過重送、`SUB_TOP_MARK` 的 snapshot/stream 雙請求、10167 延遲行情警告，皆維持現狀。

## Capabilities

### New Capabilities

- `ib-inbound-msg-loop`：IB inbound 訊息迴圈與頂層行情 tick 分派的容錯行為——單一 tick/handler 失敗不得中斷帳務與訂單回調；tick 分派不得依賴可能缺檔的 enum-switch 合成類。

### Modified Capabilities

- （無既有 spec）

## Impact

- `src/main/java/com/avalok/ib/controller/IBApiController.java`（改用 `ResilientApiController`）
- `src/main/java/com/avalok/ib/controller/ResilientApiController.java`、`InboundMsgLoop.java`（新建）
- `src/main/java/com/avalok/ib/handler/TopMktDataHandler.java`
- `src/main/java/com/avalok/ib/handler/OptionTopMktDataHandler.java`
- **不修改** `src/main/java/com/ib/`
- Redis 指令 / ACK 協定不變；`AccountMVHandler`、`AccountSummaryHandler` 的訂閱與略過邏輯不變
- 無新依賴；無 **BREAKING** API 變更
