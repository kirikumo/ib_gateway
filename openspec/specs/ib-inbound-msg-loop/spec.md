# ib-inbound-msg-loop Specification

## Purpose

保護 IB 回調通道：單一行情 tick 處理失敗時，帳務、摘要與訂單更新仍須繼續送達；頂層行情對即時與延遲同源欄位的處理必須一致，且不得因合成類載入失敗而中斷整條 inbound。

## Requirements

### Requirement: 單一回調失敗後 inbound 仍須繼續
系統 SHALL 在行情 tick 回調發生執行期錯誤、且 socket 仍連線時，繼續送達後續 IB inbound 回調。已接受的 Redis 指令 ACK MUST 不受該失敗影響而繼續送出。

#### Scenario: tick 處理錯誤不中斷帳務更新
- **WHEN** gateway 仍連線，且頂層行情 tick 回調丟出執行期錯誤
- **THEN** 後續的帳戶餘額與持倉回調 MUST 仍被處理並寫入

#### Scenario: tick 處理錯誤不中斷帳戶摘要更新
- **WHEN** 帳戶摘要訂閱仍有效，且頂層行情 tick 回調丟出執行期錯誤
- **THEN** 後續的帳戶摘要回調 MUST 仍被處理並寫入

#### Scenario: tick 錯誤後指令通道仍可用
- **WHEN** 頂層行情 tick 回調丟出執行期錯誤
- **THEN** Redis 指令（例如 `FIND_ACCOUNT_SUMMARY`）MUST 仍收到 ACK

### Requirement: 即時與延遲同源欄位行為一致
系統 SHALL 對延遲行情的買賣價、最新價、數量、成交量、最高最低收盤等欄位，套用與對應即時行情相同的處理。未知 tick 類型 MUST 被記錄，且 MUST NOT 中斷後續 inbound 訊息。

#### Scenario: 延遲買價更新頂層買價
- **WHEN** 已訂閱合約收到延遲買價 tick
- **THEN** 對外發布的頂層買價 MUST 與收到即時買價時相同方式更新

#### Scenario: 未知 tick 類型可安全忽略
- **WHEN** 收到沒有專屬處理邏輯的 tick 類型
- **THEN** 系統 MUST 記錄該類型，並繼續處理後續訊息
