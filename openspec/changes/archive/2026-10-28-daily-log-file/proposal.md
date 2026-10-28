## Why

閘道以前景方式跑，連線、下單、錯誤與 `printStackTrace()` 都只出現在終端機。程序一停或終端機關掉，畫面上看過的字就沒了。長駐程序需要把終端機看得到的輸出落在磁碟上，而且每天一個檔。同一台機器、同一個工作目錄會同時跑多個閘道，各閘道的輸出必須分開。

## What Changes

- 這個 JVM 寫到標準輸出或標準錯誤的內容，同時留在終端機，並寫進每天一個的 log 檔。包含 `DebugUtil` 的 log、`printStackInfo()`，以及直接呼叫的 `e.printStackTrace()`。終端機上的 ANSI 色碼維持；檔案裡是去掉色碼的同一段文字。
- 每個閘道一個目錄，目錄名是 `TWS_GATEWAY_NAME`，其下每天一個檔：`logs/<TWS_GATEWAY_NAME>/ib_gateway.yyyy-MM-dd.log`。例如 `start.sh` 的 `mkdata_gateway` 與 `start_angel_gw_pc.sh` 的 `angel4500_gateway` 各寫各的目錄。跨過機器本地午夜就換寫新檔，程序不用重啟。同一天、同一個閘道名稱重啟則接在當天檔案後面寫。
- `TWS_GATEWAY_NAME` 未設定、空白，或整理後不能當 `logs/` 下的單一目錄名時，目錄名用 `unknown`，也就是 `logs/unknown/ib_gateway.yyyy-MM-dd.log`。程序照舊啟動。名稱裡不適合當目錄名的字元改成 `_`，避免寫出 `logs/` 之外。
- 每個閘道保留自己最近 10 個日期（含今天）。清理只刪這個閘道目錄裡的較舊檔，不進入另一個閘道的目錄。
- 寫檔失敗時該段文字仍出現在終端機，程序繼續跑。
- 沒有印到終端機的內容不進檔。`NullIBLogger` 維持丟棄 TWS 封包。`mvn` 自己印的下載與編譯訊息是另一個程序的輸出，不進這份日檔。

## Capabilities

### New Capabilities

- `daily-log`: 把這個 JVM 印到終端機的標準輸出與標準錯誤，寫進 `logs/<TWS_GATEWAY_NAME>/` 下每天一個的檔案，並只保留該閘道最近 10 個日期。

### Modified Capabilities

- 無。既有規格沒有終端機輸出的 requirement。終端機上的色碼與行格式維持現狀。

## Impact

- 程序進入點 `App.main` 與 `GatewayController.main`：在第一行輸出之前裝上日檔轉接。呼叫 `log()` 或 `printStackTrace()` 的程式不用改。閘道名稱沿用已有的環境變數 `TWS_GATEWAY_NAME`（`BaseIBController._name` 已讀這一個）。缺這項變數時閘道仍會啟動，日檔落到 `logs/unknown/`。
- 工作目錄下的 `logs/<閘道名稱>/`（各啟動腳本都先 `cd` 到專案目錄）。
- 不新增 logging 函式庫。不改 `src/main/java/com/ib/`。`com.ib` 若自行印到標準輸出或標準錯誤，會跟著進這個閘道的日檔。
