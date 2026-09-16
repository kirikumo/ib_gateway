## 1. IB inbound 訊息迴圈

- [x] 1.1 不改 `com.ib`：`ResilientApiController` 覆寫 `connect()` 啟動 `IB-Msg-Processor`，`InboundMsgLoop.drain` 在 `IOException` 之外捕捉 `Throwable`，`Error` 包成 `RuntimeException("IB message loop", t)` 後呼叫 `error()`。`git diff src/main/java/com/ib` 應為空。

## 2. 頂層行情分派

- [x] 2.1 將 `TopMktDataHandler` 的 `tickPrice` / `tickSize` / `tickString` 從 enum `switch` 改為 `TickType` `==` if-else，合併即時與延遲同源分支，保留原本靜默的類型、未知類型打 log。`mvn compile` 後確認 `target/classes/com/avalok/ib/handler/` 不再出現 `TopMktDataHandler$4.class`，且 `javap -c TopMktDataHandler` 沒有 `$SwitchMap`。
- [x] 2.2 對 `OptionTopMktDataHandler` 的 `tickPrice` / `tickSize` / `tickString` / `tickOptionComputation` 做同樣改寫。`mvn compile` 後確認不再生成該類的 `$SwitchMap` 合成類（`OptionTopMktDataHandler$4` 若仍在，應只來自匿名 `Consumer`，`javap` 不得出現 `$SwitchMap$com$ib$client$TickType`）。

## 3. 驗證

- [x] 3.1 編譯整個專案（`mvn compile`）成功，且上述 handler 的 enum-switch 合成類已消失。
- [x] 3.2 對 `DELAYED_BID` / `BID` 與一個未知 `TickType` 走 handler 分派：延遲買價更新頂層買價的方式與即時買價相同；未知類型只打 log、不丟例外。
- [x] 3.3 在訊息迴圈中模擬 tick 回調丟 `Error` 或 `RuntimeException`：後續仍能處理帳務／摘要回調，Redis 指令（如 `FIND_ACCOUNT_SUMMARY`）仍有 ACK；執行緒名稱為 `IB-Msg-Processor` 且未退出。若無法在無 TWS 環境做完整整合，至少用可重複的單元或小型驅動驗證迴圈「捕捉後繼續抽佇列」的行為，並在任務註記未覆蓋的現場路徑。
  未覆蓋：真實 TWS 連線下的 AccountMV / AccountSummary 回調與 `FIND_ACCOUNT_SUMMARY` ACK。已用 `InboundMsgLoopTest` 驗證 `Error`/`RuntimeException` 後繼續抽佇列；`javap` 確認執行緒名 `IB-Msg-Processor`。
