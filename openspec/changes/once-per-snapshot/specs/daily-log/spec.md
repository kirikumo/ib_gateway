## ADDED Requirements

### Requirement: 當天檔不見時下一行再建
日檔轉接已安裝、程序仍在執行時，若當天 `logs/<gateway>/ib_gateway.yyyy-MM-dd.log` 的路徑不存在，或路徑上的檔已不是目前開啟的那個，系統 SHALL 在下一行終端機輸出時關掉舊的寫入並用原路徑建立檔案，且這一行 MUST 寫進該路徑上的檔。被刪掉的舊內容 MUST NOT 被補回。沒有新的終端機輸出時，系統 MUST NOT 為了補檔建立空檔。只把同一個檔清空時，後續輸出 MUST 繼續寫進該檔。日檔保留天數與換日規則 MUST 維持不變。

#### Scenario: 執行中刪掉當天檔
- **WHEN** 日檔轉接已安裝，`TWS_GATEWAY_NAME` 是 `mkdata_gateway`，當天檔已有內容，該檔在程序仍執行時被刪除，且程序再寫出一行
- **THEN** `logs/mkdata_gateway/ib_gateway.yyyy-MM-dd.log` 再次存在，這一行為其內容，刪除前的內容不在檔裡

#### Scenario: 路徑上換成另一個檔
- **WHEN** 當天檔在程序仍執行時被刪除，且在下一行輸出前，同一路徑上出現另一個檔
- **THEN** 這一行事寫進該路徑上的檔，而不是寫進已經從目錄消失的舊檔

#### Scenario: 刪掉之後沒有新輸出
- **WHEN** 當天檔在程序仍執行時被刪除，且程序沒有再寫出終端機輸出
- **THEN** 系統不建立空的當天檔

#### Scenario: 只清空同一個檔
- **WHEN** 當天檔被清空但沒有換成另一個檔，且程序再寫出一行
- **THEN** 這一行事寫在被清空的那個檔裡
