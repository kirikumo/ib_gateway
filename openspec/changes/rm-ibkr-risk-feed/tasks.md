## 1. 風控 JSON 與 Redis 出口

- [x] 1.1 做風控專用的 JSON 字串出口，序列化時保留 `null`，並能 `HSET`、`HDEL`、`HGETALL`、`PUBLISH` 同一段字串。驗證：單元測試寫入含 `price: null` 的物件後，讀回的欄位仍是 JSON `null`，不是省略，也不是再包一層字串。
- [x] 1.2 做每個 `orderRef` 的 `updateTimeMs`：新值必須大於上一筆，同一毫秒則上一筆加 1；記憶體沒有上一筆時可從既有 hash JSON 讀出。驗證：同一毫秒連寫兩筆，第二筆等於第一筆加 1；給定 hash 舊值 100，新時鐘 100 時寫出 101。

## 2. 訂單文件

- [x] 2.1 組訂單 JSON：`orderRef`（`uranus_` / `api_` 原樣，否則 `tws:` 加 permId，permId 0 不產生）、狀態表、`PreCancelled` 為 `cancelling`、`WarnState` 不改 status、價格表、小數數量、pair、持倉用不到的組合腿、未設定的 `0` 與 `Double.MAX_VALUE` 寫成 `null`。不呼叫 `toOMSJSON()`、`isAlive()`、`IBContract.pair()`。驗證：單元測試覆蓋 `TRAIL`、`TRAIL LIMIT`、`STP`、`BAG` 淨價為負、期貨 8 碼未齊時 `pair` 為 `null`、乘數為 1 的期貨 pair 仍含 `@1`、permId 0 沒有輸出。
- [x] 2.2 連線期間的寫入順序：`new` / `cancelling` 先 `HSET` 再 `PUBLISH`；`filled` / `cancelled` / `rejected` 先 `HDEL` 再 `PUBLISH`；佣金晚到只 `PUBLISH` 且 `updateTimeMs` 更大。驗證：假 Redis 記錄操作順序，終態是刪除在推送之前，佣金推送後 hash 不再有該 field。

## 3. 重連對帳

- [x] 3.1 對帳屏障等這次連線的 `openOrderEnd` 與 `completedOrdersEnd` 都到齊才處理殘留 field。OMS 仍在 `openOrderEnd` 初始化。驗證：只送達其中一個結束信號時，假 Redis 沒有 `removed`；兩個都到齊後才出現。
- [x] 3.2 殘留 field 對當日完結單與當日成交：`Filled`、`Cancelled`（含零成交）、`Rejected`、否則 `removed`。先推送再刪除。從未在 hash 裡的完結單不推。驗證：四種對帳結果的單元測試，以及一張只出現在完結單、不在 hash 的單沒有推送。

## 4. 餘額與摘要

- [x] 4.1 組這一輪快照：`CashBalance` 跳過 `BASE` 與空幣別、無後綴 `NetLiquidation` 與其 currency、持倉 key 五種情況、張數 0 不寫、無市價為 `null`、張數保留小數、沒有 `openCount`、沒有 `BAG` 列。驗證：主上市地空且交易所為 `HKFE` 時 key 以 `HKFE:` 開頭；`conId` 為 0 且 pair 組不出的列不出現；`AccountReady=false` 的組裝結果標記為不可寫。
- [x] 4.2 只在該戶 `accountDownloadEnd`、這輪沒有 `AccountReady=false`、且有無後綴 `NetLiquidation` 時，用同一個 `updateTimeMs` 寫入 balance 與 summary。否則兩個 `RM:IBKR` 鍵不動。既有 `IBGateway:{account}:balance` 仍在回呼時按原格式寫。驗證：End 前與 `AccountReady=false` 的 End 都不寫新鍵；成功的 End 兩個鍵時間相同；舊鍵寫入次數與變更前的回呼規則一致。

## 5. 接上閘道

- [x] 5.1 `runAccountMvCycle` 每戶先停止上一訂閱，再訂閱，等到該戶 `accountDownloadEnd`、60 秒逾時或斷線才換下一戶。focus 不是最後一戶時，本輪結束再訂一次並同樣等待。上一輪未完時 30 秒計時器與 `UPDATE_ACCOUNT_MV` 不並行。等待不在秒級心跳計時器裡。驗證：用假的帳戶處理器，兩戶時第二戶的 `true` 發生在第一戶 End 之後；逾時後該戶沒有新的 `RM:IBKR` 寫入，循環仍繼續；心跳任務沒有 sleep。
- [x] 5.2 秒級計時器在 `isRealConnected()` 時約每 5 秒 `SETEX` `RM:IBKR:{name}:connection` 15 秒，只含 `updateTimeMs`。工作階段不在則不寫。`IBGateway:{name}:status` 與 `{name}_CONNECTION` 維持原樣。驗證：假時鐘連續叫兩次未滿 5 秒只寫一次；斷線後不再寫；status 鍵仍每秒寫入。
- [x] 5.3 在 `AllOrderHandler` 既有寫入之後呼叫訂單旁路，含成交、佣金、錯誤碼造成的拒絕或撤銷，以及合約明細補齊後的再推。不改 `com.ib`，不改 `reqCompletedOrders(false)`。驗證：`git diff` 沒有 `src/main/java/com/ib/`；一筆明細補齊後同一 `orderRef` 再推且 `updateTimeMs` 更大。
- [x] 5.4 在 `AccountMVHandler` 既有寫入之後把回呼交給帳戶旁路。驗證：增量 `updatePortfolio` 會寫舊 balance，不會寫 `RM:IBKR:{account}:balance`。

## 6. 整體驗收

- [x] 6.1 跑 `mvn test`。驗證：命令結束碼為 0，且上述新增測試都在其中。
