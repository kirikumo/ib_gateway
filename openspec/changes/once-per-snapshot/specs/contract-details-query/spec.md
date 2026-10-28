## Purpose

帳戶快照與下單會反覆帶出同一批合約。這份規格規定已取得的合約何時不再向 IB 重查，以及何時才刷新 Redis 上的合約快取。

## ADDED Requirements

### Requirement: 二十四小時內的合約不再向 IB 重查
系統 SHALL 以 `IBGateway:Contract:` 後面那一段合約名稱記住成功取得的合約細節。同一名稱再次出現，且距離上次成功取得短於 24 小時時，系統 MUST NOT 向 IB 送出該名稱的 `reqContractDetails`，也 MUST NOT 重寫 `IBGateway:Contract:{名稱}` 的內容或 TTL。呼叫端 MUST 仍讀得到這份已記住的細節。這個名稱若還會連帶再查一次（例如 SMART 再查沒有交易所的合約），那些連帶查詢也 MUST NOT 送出。

#### Scenario: 三十秒後同一持倉再出現
- **WHEN** `SEHK:STK:HKD-1` 的細節已在 24 小時內成功取得，且帳戶快照再次帶出這個名稱
- **THEN** 系統 MUST NOT 對 `SEHK:STK:HKD-1` 送出 `reqContractDetails`，且 MUST NOT 重寫 `IBGateway:Contract:SEHK:STK:HKD-1`

#### Scenario: 下單或依快取查詢命中同一份細節
- **WHEN** 下單或依快取查詢合約時，該合約名稱已在 24 小時內成功取得
- **THEN** 呼叫端 MUST 拿到已記住的細節，且系統 MUST NOT 再向 IB 重查

#### Scenario: 快取命中時不印逐筆查詢日誌
- **WHEN** 同一合約名稱在 24 小時內再次出現
- **THEN** 系統 MUST NOT 為這次出現印出合約查詢或合約 Redis 寫入日誌

### Requirement: 沒有成功細節的合約立刻查
系統 SHALL 在記憶體沒有該合約名稱的成功細節時向 IB 查詢。沒有成功時間的快取 MUST 視同沒有成功細節。同一名稱在同一次爆發裡再次出現時，系統 MUST NOT 在前一次查詢還在冷卻時再送一次。

#### Scenario: 第一次見到的持倉
- **WHEN** 帳戶快照帶出一個記憶體裡沒有成功細節的合約名稱
- **THEN** 系統 MUST 向 IB 查詢該合約

#### Scenario: 同一秒內同一名稱出現兩次
- **WHEN** 同一個尚未成功取得的合約名稱在同一秒內出現第二次
- **THEN** 系統 MUST NOT 送出第二次 `reqContractDetails`

### Requirement: 滿二十四小時才刷新
距離上次成功取得已達 24 小時的合約，系統 SHALL 在該名稱再次出現時再向 IB 查一次。成功回覆後 MUST 重寫對應的 `IBGateway:Contract:{名稱}`，TTL 與現況相同。回覆尚未到達時，讀取端 MUST 仍使用上一份成功細節。

#### Scenario: 過期後的下一次出現
- **WHEN** 已記住的合約距離上次成功取得已達 24 小時，且該名稱再次出現
- **THEN** 系統 MUST 再向 IB 查詢，成功後 MUST 重寫 `IBGateway:Contract:{名稱}`

#### Scenario: 刷新查詢尚未回來
- **WHEN** 已送出刷新查詢，但 IB 尚未回覆
- **THEN** 讀取端 MUST 仍拿到上一份成功細節
