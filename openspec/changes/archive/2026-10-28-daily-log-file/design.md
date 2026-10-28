## Context

見 proposal.md 的 Why。終端機上的字來自這個 JVM 的標準輸出與標準錯誤：`DebugUtil.log()` 組出 `MM/dd-HH:mm:ss.SSS`、呼叫端位置與訊息後 `System.out.println`；`err` / `info` / `warn` 先包 ANSI 色碼；`printStackInfo()` 另走 `System.out`；`e.printStackTrace()` 走 `System.err`。`NullIBLogger` 不印 TWS 封包。進入點有兩個：`App.main` 會再呼叫 `GatewayController.main`。閘道名稱已經是環境變數 `TWS_GATEWAY_NAME`，`BaseIBController._name` 讀的就是它；啟動腳本各自匯出不同的值，例如 `mkdata_gateway`、`angel4500_gateway`、`laura_gateway`、`PhD_gateway`。這些腳本都 `cd` 到同一個專案目錄。`pom.xml` 沒有 logging 函式庫。`.gitignore` 沒有 log 目錄。

## Goals / Non-Goals

**Goals:**

- 日檔轉接裝上之後，標準輸出與標準錯誤的內容都進這個閘道自己的當天檔，行為以 `specs/daily-log/spec.md` 為準。
- 同一個工作目錄裡，不同 `TWS_GATEWAY_NAME` 同時跑時各寫 `logs/<名稱>/`，清理也只動自己目錄裡的舊檔。
- 換日、保留 10 個日期、寫檔失敗都不需要額外執行緒。既有 `println` 與 `printStackTrace()` 呼叫點不用改。

**Non-Goals:**

- 不改 `NullIBLogger`，不把 TWS 封包印出來。
- 不接收 `mvn` 父程序的下載與編譯輸出。
- 不改 `src/main/java/com/ib/` 的原始碼。該樹若印到標準輸出或標準錯誤，由轉接抄進這個閘道的日檔。
- 不引入 logging 函式庫，也不做依大小切開的輪轉。
- 不把同名的兩個程序再拆開。相同 `TWS_GATEWAY_NAME` 仍共用 `logs/<名稱>/` 裡的同一個當天檔。

## Decisions

### 轉接標準輸出與標準錯誤，不在每個 log 呼叫裡寫檔

新增一個日檔轉接，在進入點換成 `System.out` / `System.err`。原本的串流留下，終端機照寫；檔案側另寫一份去掉色碼的文字。`DebugUtil` 繼續 `println`，不再自己開檔，避免一行進檔兩次。

只包 `DebugUtil.log()` 會漏掉 `e.printStackTrace()` 以及 `com.ib` 直接印出的字。JUL `FileHandler` 只有依大小輪轉，也收不到已經直接寫到 `System.out` 的呼叫。

### 一個閘道一個目錄

路徑是 `{user.dir}/logs/<gateway>/ib_gateway.yyyy-MM-dd.log`。`<gateway>` 在安裝時讀 `TWS_GATEWAY_NAME`，去掉首尾空白。每個不是英數、`_`、`-` 或 `.` 的字元改成 `_`。未設定、空白，或整理後是空的、`.` 或 `..`，目錄名用 `unknown`。目錄名只作為 `logs/` 底下的一個路徑片段，不跟著環境變數走出 `logs/`。安裝之後名稱不再重讀；`App.main` 與接著呼叫的 `GatewayController.main` 是同一個程序、同一組環境變數。缺名稱時閘道仍啟動，日檔在 `logs/unknown/`。

檔名不再重複閘道名稱。名稱在目錄上，`ls logs/` 就是正在寫 log 的閘道。把名稱留在檔名裡會和目錄重複。

### 兩個進入點都裝，而且只裝一次

`App.main` 與 `GatewayController.main` 的第一個動作都是安裝。沒有可用的 `TWS_GATEWAY_NAME` 時目錄用 `unknown`，不因此結束程序。`App` 反射呼叫 `GatewayController.main` 時會再安裝一次；第二次發現已經換過的串流就返回，沿用第一次讀到的閘道名稱。不放在 `DebugUtil` 的靜態初始化，避免測試一載入類別就改掉整個 JVM 的標準輸出。

### 檔名帶日期，午夜直接開新檔

本地日期用 `ZoneId.systemDefault()`。檔案側累積到換行，再去掉色碼、以 UTF-8 append 寫入並 flush。第一次需要寫某一天時才建立 `logs/<gateway>/` 並打開檔案。日期與目前打開的檔不同，就關掉舊的、打開這個閘道目錄裡的新日期檔，然後做保留清理。沒有計時器；沒有新的終端機輸出就不建空白隔日檔。

Python `TimedRotatingFileHandler` 是固定主檔名、午夜再改名。這裡檔名本身就是那一天，閘道則是它的上層目錄。

### 終端機留色碼，檔案按行去掉 CSI 色碼

檔案那一行只刪 `\u001B\[[0-9;]*m`。整行到齊再刪，避免色碼被拆成兩次 `write` 時刪不乾淨。`DebugUtil` 與 `printStackTrace()` 都是整行送出。訊息裡其他控制字元原樣進檔。

### 同一把鎖罩住檔案裡的一行

這個程序的標準輸出與標準錯誤共用這把鎖與同一個檔。鎖內完成：寫進原本的終端機串流、把檔案側湊成一行、去色碼、寫檔並 flush。這個 JVM 裡一行不會被另一行拆開。不同閘道名稱是不同目錄，不靠這把鎖分開。

`DebugUtil` 的時間改用 `DateTimeFormatter`（`MM/dd-HH:mm:ss.SSS`，本地時區），不再用共用的 `SimpleDateFormat`。否則多條回調同時 `log()` 時，終端機與檔案裡的時間會壞掉。

### 保留 10 個日期只清這個閘道的目錄

清理在兩次發生：這個程序第一次成功打開檔時，以及換日打開新檔時。只列出 `logs/<這個閘道>/` 裡檔名符合 `ib_gateway.yyyy-MM-dd.log` 且日期能解析的檔。刪除條件是檔名日期早於 `今天.minusDays(9)`。`mkdata_gateway` 在 2026-10-28 會刪自己目錄裡的 2026-10-18 與更早的檔，留下自己的 10-19 到 10-28，並且不列出 `logs/angel4500_gateway/`。解析失敗、樣式不符的檔案留著。停著沒跑的閘道不會被別人清掉；它下次啟動時清自己的目錄。

### 寫檔失敗只跳過檔案

終端機側先寫。建立目錄、開檔或寫入丟出的例外吞掉，且不得再經由標準輸出印出這次失敗，以免轉接呼叫自己。該段文字已經在終端機上，程序不退出。下一行再試，磁碟恢復後不用重啟。

### 測試可以換目錄、今天與閘道名稱，並還原串流

正式路徑、時鐘與名稱維持 `logs/`、`LocalDate.now(ZoneId.systemDefault())` 與 `TWS_GATEWAY_NAME`。另留只給測試用的目錄、日期、閘道名稱注入與還原標準串流（註解標 Test only）。測試在 `finally` 還原，避免同一次 `mvn test` 裡其他測試的輸出被寫進暫存日檔。

### logs 目錄不進版控

`.gitignore` 加上 `/logs/`。執行時產生的閘道子目錄與日檔不提交。

## Risks / Trade-offs

- [兩個程序用了同一個 `TWS_GATEWAY_NAME`] → 仍 append `logs/<名稱>/` 裡的同一個日期檔，行可能互相插入。不同名稱則分開目錄。
- [系統鐘跳日] → 換日與刪檔都跟本地今天走。鐘往前跳超過 10 天會把這個閘道還想留的舊檔刪掉。依賴機器本身的對時。
- [安裝前就印出的字] → 進不了日檔。兩個 `main` 都把安裝放在第一個動作。
- [名稱在安裝時就固定] → 程序中途改環境變數不會換目錄。啟動腳本在 JVM 起來前就匯出名稱。
- [色碼正則過寬] → 訊息裡若含同樣形式的 ESC 序列，檔案裡會被拿掉。現有色碼輔助方法就是這個形式。
- [寫檔每次換行就 flush] → 多一次磁碟寫。現有輸出不是逐筆行情，可以接受。

## Migration Plan

重新啟動各閘道後，安裝之後的終端機輸出開始進 `logs/<名稱>/ib_gateway.<日期>.log`。重啟前的畫面沒有檔案可回補。回復方式是還原轉接、兩個 `main` 與 `.gitignore` 後重啟；留下的 `logs/` 可以整目錄刪掉，程序不讀舊檔。
