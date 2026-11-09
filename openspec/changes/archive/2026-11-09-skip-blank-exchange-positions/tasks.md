# Tasks

## 1. 空交易所的持倉

- [x] 1.1 在 `ContractDetailsHandler` 依非零 `conid` 從 `KNOWN_CONTRACTS` 取上市交易所。略過空字串與 `SMART`；先用已知合約的 `exchange`，沒有再用 `primaryExch`。`conid` 為 0 回傳 null。不改 `src/main/java/com/ib/`。驗證：同 `conid` 有 `SEHK` 時得到 `SEHK`；只有 `SMART` 且沒有非 `SMART` 的 `primaryExch` 時得到 null。
- [x] 1.2 在 `IBContract` 補上交易所後重算 `shownName`。只改欄位而不重算時，名字仍會是建構當下的 `null:`。驗證：`exchange` 為 null 的 `STK`/`HKD`/`7200` 在補上 `SEHK` 後，`shownName` 為 `SEHK:STK:HKD-7200`。
- [x] 1.3 `AccountMVHandler.updatePortfolioBody`：`exchange` 為 null 或空字串時先借上市交易所；借到就用重算後的 key 更新該筆。借不到不 `put`，仍呼叫既有的明細查詢。空交易所的合約不放進 `ibc_cache`。有 `exchange`（含 `SMART`）的持倉維持原 key。驗證：借到 `SEHK` 時 balance 只有 `SEHK:STK:HKD-7200`；借不到時不新增該筆，同戶已有的其他持倉 key 還在；`SMART` 持倉不被改寫。

## 2. 寫入前清掉壞 key

- [x] 2.1 `writePosition` 不刪已在該戶 map 裡、key 以 `null:` 開頭的項目，也不清空整張表。不改 TTL `2764800`，不改只在該戶 `accountDownloadEnd` 寫一次。不改 `RmAccountFeed`。驗證：map 裡同時有 `null:STK:HKD-7200` 與 `SEHK:STK:HKD-7200` 時，寫出的內容兩者都在，現金列也在；這一輪全部借不到時，先前的 `SEHK` key 仍被寫出。
- [x] 2.2 用 `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn -Dtest=AccountMVHandlerRmTest,ContractDetailsHandlerQueryTest test` 跑既有測試。測試若把合約放進 `KNOWN_CONTRACTS`，結束時清掉，避免影響其他測試。驗證：上述測試通過，且 `successfulEndSharesTimestampAndIncrementalPortfolioKeepsRm` 仍在 End 才寫舊鍵、內容仍含 `NASDAQ:STK:USD-AAPL`。
