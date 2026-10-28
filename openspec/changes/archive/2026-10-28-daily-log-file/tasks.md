## 1. 日檔轉接

- [x] 1.1 新增日檔轉接並安裝到標準輸出與標準錯誤：終端機照寫原本的串流，檔案側湊滿一行後去掉 `\u001B\[[0-9;]*m`，以 UTF-8 append 寫入 `logs/<gateway>/ib_gateway.yyyy-MM-dd.log`。`<gateway>` 取自 `TWS_GATEWAY_NAME` 去掉首尾空白；每個不是英數、`_`、`-` 或 `.` 的字元改成 `_`；未設定、空白，或整理後是空的、`.` 或 `..`，目錄名用 `unknown`。沒有 `logs/<gateway>/` 就建立；每行 flush；開檔或寫入失敗則吞掉且不得再經標準輸出印出該失敗，下一行再試。標準輸出與標準錯誤共用一把鎖與這個閘道的同一個當天檔。安裝必須可重複呼叫而不把同一行寫進檔兩次，第二次沿用第一次的閘道名稱。正式路徑、今天與名稱維持工作目錄、`LocalDate.now(ZoneId.systemDefault())` 與環境變數；另留只給測試的目錄、日期、閘道名稱注入與串流還原，註解標 Test only。驗證：測試把目錄指到暫存路徑、名稱設為 `mkdata_gateway` 並安裝轉接後，向標準輸出寫出含色碼的一行，終端機字串含色碼，`mkdata_gateway/ib_gateway.<今天>.log` 不含色碼且其餘文字相同；向標準錯誤呼叫 `printStackTrace()` 後，終端機上的堆疊文字也在同一個檔；`logs/mkdata_gateway/` 原本不存在時會被建立；安裝兩次後再寫一行，檔裡該行只出現一次；未設定、空白與 `..` 都寫進 `unknown/ib_gateway.<今天>.log`。兩條執行緒同時各寫一行時，檔內兩行各自完整。

- [x] 1.2 不同閘道名稱寫入不同目錄。驗證：同一個暫存目錄先以 `mkdata_gateway` 寫一行，還原後再以 `angel4500_gateway` 寫另一行，兩行分別只出現在 `mkdata_gateway/ib_gateway.<今天>.log` 與 `angel4500_gateway/ib_gateway.<今天>.log`。

- [x] 1.3 打開中的檔案日期與注入的今天不同時，關掉舊檔、打開這個閘道目錄裡的新日期檔，並接寫同一天已有內容。沒有新輸出時不建空白隔日檔。驗證：名稱固定為 `mkdata_gateway`，注入 2026-10-28 寫一行，再注入 2026-10-29 寫一行，兩檔各有自己那一行，28 號檔內容與跨日前相同；同一天還原後以相同名稱再次安裝並寫一行，新行在舊內容後面。

- [x] 1.4 第一次打開檔以及換日打開新檔時，只刪除 `logs/<gateway>/` 裡檔名日期早於今天減 9 天的 `ib_gateway.yyyy-MM-dd.log`。其他閘道目錄與樣式不符的檔案留著。驗證：今天注入 2026-10-28、名稱是 `mkdata_gateway`，先放 `mkdata_gateway/ib_gateway.2026-10-18.log`、`mkdata_gateway/ib_gateway.2026-10-19.log`、`angel4500_gateway/ib_gateway.2026-10-18.log` 與 `mkdata_gateway/notes.txt`，寫一行後自己的 10-18 消失，自己的 10-19、`angel4500_gateway` 的 10-18 與 `notes.txt` 仍在，新行在 `mkdata_gateway/ib_gateway.2026-10-28.log`。

- [x] 1.5 `DebugUtil.log()` 的時間改用本地時區的 `DateTimeFormatter`（`MM/dd-HH:mm:ss.SSS`），不再用共用的 `SimpleDateFormat`。驗證：多執行緒同時呼叫 `log()` 不丟出例外，寫出的時間前綴符合 `MM/dd-HH:mm:ss.SSS`。

- [x] 1.6 在 `App.main` 與 `GatewayController.main` 的第一個動作安裝日檔轉接，放在任何 `log`、Redis 檢查或 `new GatewayController()` 之前。驗證：讀這兩個 `main`，安裝是方法裡的第一個呼叫。

## 2. 範圍

- [x] 2.1 `.gitignore` 加上 `/logs/`。驗證：`git check-ignore -v logs/mkdata_gateway/ib_gateway.2026-10-28.log` 命中這條規則。

- [x] 2.2 不改 `NullIBLogger`、不改 `src/main/java/com/ib/`、不改既有 `e.printStackTrace()` 呼叫點。驗證：`git diff --stat` 沒有 `src/main/java/com/ib/` 與 `NullIBLogger.java`；`GatewayController`、`BaseIBController`、`Redis` 裡既有的 `printStackTrace()` 仍在。

- [x] 2.3 跑 `mvn test`。驗證：命令結束碼為 0，且 1.1 到 1.5 的新測試在其中。每個測試的 `finally` 還原標準輸出、標準錯誤、目錄、日期與閘道名稱注入。
