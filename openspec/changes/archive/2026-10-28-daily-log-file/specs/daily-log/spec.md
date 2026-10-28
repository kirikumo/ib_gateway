## Purpose

把這個閘道 JVM 印到終端機的標準輸出與標準錯誤，依 `TWS_GATEWAY_NAME` 分目錄留在每天一個的檔案裡，並只保留該閘道最近 10 個本地日期，讓同時運行的多個閘道停掉之後仍查得到各自畫面上出現過的 log、錯誤與堆疊。

## ADDED Requirements

### Requirement: 終端機輸出同時進當天檔案
日檔轉接安裝之後，這個 JVM 寫到標準輸出或標準錯誤的內容 SHALL 同時留在終端機，並寫進這個閘道當天的 log 檔。檔案路徑 MUST 是工作目錄下的 `logs/<gateway>/ib_gateway.yyyy-MM-dd.log`。`<gateway>` MUST 是環境變數 `TWS_GATEWAY_NAME` 去掉首尾空白後的值。名稱中每個不是英數、`_`、`-` 或 `.` 的字元 MUST 改成 `_`。日期為機器本地日曆日。第一段輸出寫入時若 `logs/<gateway>/` 不存在，系統 MUST 建立它。標準輸出與標準錯誤 MUST 寫進這個閘道的同一個當天檔案。

#### Scenario: 標準輸出的一行
- **WHEN** 日檔轉接已安裝，`TWS_GATEWAY_NAME` 是 `mkdata_gateway`，且程序向標準輸出寫出一行
- **THEN** 終端機有這一行，且 `logs/mkdata_gateway/ib_gateway.yyyy-MM-dd.log`（yyyy-MM-dd 為本地今天）也有這一行

#### Scenario: 標準錯誤的堆疊
- **WHEN** 日檔轉接已安裝，且程序呼叫 `e.printStackTrace()`
- **THEN** 終端機看得到的堆疊文字也在這個閘道的當天 log 檔裡

#### Scenario: 目錄尚不存在
- **WHEN** 日檔轉接已安裝，工作目錄下沒有 `logs/mkdata_gateway/`，`TWS_GATEWAY_NAME` 是 `mkdata_gateway`，且程序寫出第一段輸出
- **THEN** 系統建立 `logs/mkdata_gateway/`，並把該段寫進當天檔案

### Requirement: 沒有閘道名稱時寫入 unknown
`TWS_GATEWAY_NAME` 未設定、去掉首尾空白後是空的，或整理後是空的、`.` 或 `..` 時，系統 SHALL 把輸出寫進 `logs/unknown/ib_gateway.yyyy-MM-dd.log`。程序 MUST 照舊啟動。

#### Scenario: 環境變數未設定
- **WHEN** 日檔轉接已安裝，`TWS_GATEWAY_NAME` 未設定或只有空白，且程序寫出一行
- **THEN** 該行寫進 `logs/unknown/ib_gateway.yyyy-MM-dd.log`，程序繼續執行

### Requirement: 同時運行的閘道各寫各的目錄
不同 `TWS_GATEWAY_NAME` 的程序 SHALL 把同一天的輸出寫進不同目錄。一個閘道的寫入 MUST NOT 追加到另一個閘道目錄裡的當天檔。

#### Scenario: 兩個閘道同一天各寫一行
- **WHEN** `mkdata_gateway` 與 `angel4500_gateway` 在同一個本地日期各向終端機寫出一行
- **THEN** 第一行只在 `logs/mkdata_gateway/ib_gateway.yyyy-MM-dd.log`，第二行只在 `logs/angel4500_gateway/ib_gateway.yyyy-MM-dd.log`

### Requirement: 檔案去掉 ANSI 色碼
終端機上的輸出 SHALL 保留既有 ANSI 色碼與既有內容。寫進檔案的對應文字 MUST 是去掉 ANSI 色碼之後的內容，其餘字元相同。

#### Scenario: 著色的錯誤行
- **WHEN** 日檔轉接已安裝，且程序向標準輸出寫出含 ANSI 色碼的一行
- **THEN** 終端機仍含那些色碼，這個閘道的當天檔案裡的對應行沒有色碼，其餘文字與終端機相同

### Requirement: 每天一個檔
系統 SHALL 把同一個閘道、本地同一天的終端機輸出寫進同一個檔案。本地日期變了之後的下一段輸出 MUST 寫進該閘道目錄下新日期的檔案，且 MUST NOT 改寫前一天檔案已有的內容。同一天、同一個閘道名稱的程序重新啟動後 MUST 接在當天檔案已有內容之後寫，MUST NOT 清空該檔。沒有新的終端機輸出時，系統 MUST NOT 為了換日而主動建立空白檔。

#### Scenario: 跨過本地午夜
- **WHEN** 日檔轉接已安裝，`TWS_GATEWAY_NAME` 是 `mkdata_gateway`，本地日期從 2026-10-28 變成 2026-10-29 之後再寫出一行
- **THEN** 該行在 `logs/mkdata_gateway/ib_gateway.2026-10-29.log`，`logs/mkdata_gateway/ib_gateway.2026-10-28.log` 仍保留跨日之前的內容

#### Scenario: 同一天重啟
- **WHEN** 這個閘道的當天檔案已有內容，程序以同一個 `TWS_GATEWAY_NAME` 重新啟動並再次安裝日檔轉接後再寫出一行
- **THEN** 當天檔案保留重啟前的內容，新行在後面

### Requirement: 只保留該閘道最近 10 個日期
系統 SHALL 在這個程序第一次把輸出寫入日檔時，以及換日改開新檔時，只在 `logs/<gateway>/` 裡刪除日期早於「本地今天減 9 天」的 `ib_gateway.yyyy-MM-dd.log`。本地今天與今天往前數 9 天（含這兩天）的該閘道檔案 MUST 保留。另一個閘道的目錄，以及這個目錄裡檔名不是這個樣式的檔案，MUST 留著。

#### Scenario: 刪掉這個閘道的第 11 天
- **WHEN** 本地日期是 2026-10-28，`TWS_GATEWAY_NAME` 是 `mkdata_gateway`，`logs/mkdata_gateway/` 裡有 `ib_gateway.2026-10-18.log` 與 `ib_gateway.2026-10-19.log`，日檔轉接已安裝，且程序再寫出一行
- **THEN** `logs/mkdata_gateway/ib_gateway.2026-10-18.log` 被刪除，`logs/mkdata_gateway/ib_gateway.2026-10-19.log` 仍在，新行寫進 `logs/mkdata_gateway/ib_gateway.2026-10-28.log`

#### Scenario: 不刪另一個閘道的舊檔
- **WHEN** 本地日期是 2026-10-28，`TWS_GATEWAY_NAME` 是 `mkdata_gateway`，`logs/angel4500_gateway/ib_gateway.2026-10-18.log` 存在，且 `mkdata_gateway` 寫出一行並執行清理
- **THEN** `logs/angel4500_gateway/ib_gateway.2026-10-18.log` 仍在

#### Scenario: 其他檔案不動
- **WHEN** `logs/mkdata_gateway/` 裡有不符合 `ib_gateway.yyyy-MM-dd.log` 的檔案，且該閘道寫出一行並執行清理
- **THEN** 該檔案仍在

### Requirement: 寫檔失敗不影響程序
當天檔案無法建立或寫入時，系統 SHALL 仍把該段文字印到終端機，且 MUST NOT 因為這次寫檔失敗而結束程序。之後的輸出 MUST 繼續嘗試寫檔。

#### Scenario: 檔案寫不進去
- **WHEN** 日檔轉接已安裝，且這個閘道的當天 log 檔無法建立或寫入
- **THEN** 該段文字仍出現在終端機，程序繼續執行

### Requirement: 同一行不被拆開
多條執行緒同時寫向標準輸出或標準錯誤時，系統 SHALL 讓每一行在這個閘道的檔案裡保持完整。一行的字元 MUST NOT 插入另一行中間。

#### Scenario: 兩條執行緒同時寫
- **WHEN** 日檔轉接已安裝，且兩條執行緒同時各向終端機寫一行
- **THEN** 這個閘道的當天檔案裡這兩行各自完整

### Requirement: 沒有印到終端機的內容不進日檔
沒有寫到標準輸出或標準錯誤的內容 SHALL NOT 出現在日檔。TWS API 進出封包 MUST 繼續被丟棄，MUST NOT 因為封包而增加日檔內容。

#### Scenario: TWS 封包仍不記錄
- **WHEN** API 連線有進出封包，且這些封包沒有寫到標準輸出或標準錯誤
- **THEN** 日檔不因這些封包而增加內容
