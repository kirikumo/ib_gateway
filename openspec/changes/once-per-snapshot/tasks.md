## 1. 已知道的合約不再重查

- [x] 1.1 在 `ContractDetailsHandler.queryDetails` 開頭用合約名稱看 `KNOWN_CONTRACT_DETAILS`。有 `_timestamp` 且距離現在短於 24 小時就返回：不印日誌、不寫 `QUERY_HIS`、不呼叫 `queryContractList`（SMART 的第二次查詢與 combo 腿也不送）。沒有這筆、沒有 `_timestamp`、或已滿 24 小時，才走現有的 10 秒冷卻。24 小時做成測試讀得到的常數。`queryContractListToRedis` 不改。驗證：讀 `queryDetails`，新鮮名稱在碰到 `GW_CONTROLLER` 之前返回。
- [x] 1.2 為上面的判斷加測試，放在 `com.avalok.ib.handler`，不要 `new GatewayController()`，也不要載入 `com.bitex.util.Redis`。驗證：`SEHK:STK:HKD-1` 在 24 小時內時呼叫 `queryDetails` 不會把該名稱放進 `QUERY_HIS`，`GW_CONTROLLER` 為 null 也不會丟錯；沒有 `_timestamp` 與已滿 24 小時的快取被判成需要查，且這兩個案例不接上真正的 controller。

## 2. 舊餘額鍵只在該戶 End 寫一次

- [x] 2.1 `AccountMVHandler` 的現金與持倉回呼只改記憶體，不再呼叫寫入。`accountDownloadEnd(account)` 才寫 `IBGateway:{account}:balance`，只寫該戶，TTL 仍是 2764800。該戶不在 `_data` 就不寫。`RmAccountFeed.onDownloadEnd` 仍在這次寫入之後，可寫條件不動。拿掉持倉回呼、現金回呼，以及 balance 寫入的逐筆日誌；保留 End 那一行。不改 `src/main/java/com/ib/`。驗證：回呼方法裡沒有寫入呼叫；End 只對該 `account` 呼叫 writer 一次。
- [x] 2.2 更新 `AccountMVHandlerRmTest`。`successfulEndSharesTimestampAndIncrementalPortfolioKeepsRm` 改成：初始化之後的 `updatePortfolio` 不增加舊鍵寫入，下一次該戶 `accountDownloadEnd` 才再寫一次，而且這筆回呼到 End 之前 `RM:IBKR` 餘額仍是上一份。`portfolioBeforeEndWritesNeitherKey` 與 `accountReadyFalseEndKeepsPreviousRmAndStillWritesOldBalance` 的預期維持。補一則：A 已寫入之後，B 的多筆回呼不寫入，B 的 End 只多出 `IBGateway:B:balance`。驗證：這三則舊測試加新測試都通過。

## 3. 整體驗收

- [x] 3.1 用 Java 17 以上跑 `mvn test`。驗證：結束碼為 0，且 1.2 與 2.2 的測試都在這次執行裡。

## 4. 當天日檔刪了要再生

- [x] 4.1 `DailyLog.ensureOpen` 在沿用當天 writer 之前，若路徑不存在，或路徑上的檔與已打開的不是同一個，就關掉舊 writer，再用原路徑 `CREATE` + `APPEND`。`fileKey` 不可用時只看路徑是否存在。只清空同一個檔時不重開。沒有新輸出不建檔。不改保留天數。驗證：讀 `ensureOpen`，刪檔或換成另一個檔的下一行會走建立路徑。
- [x] 4.2 在 `DailyLogTest` 加案例，不要載入 `com.bitex.util.Redis`。驗證：當天檔寫過一行後刪除，再寫一行，原路徑的檔只含這一行；刪除後在下一行之前放上另一個同名檔，這一行在該路徑上；刪除後沒有再寫出時，測試沒有為了補檔而建出空檔；把同一個檔清空再寫一行，內容接在這個檔裡。
- [x] 4.3 用 Java 17 以上跑 `mvn -Dtest=DailyLogTest test`。驗證：結束碼為 0，且 4.2 的案例在這次執行裡。
