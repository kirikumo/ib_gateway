## Purpose

定義帳戶市值如何向 TWS 取值，以及 Redis `IBGateway:{account}:balance` 何時刷新，讓下游讀到的現金與持倉市值不會停在連線當下。

## ADDED Requirements

### Requirement: 連線後立即刷新各帳戶市值
系統 SHALL 在與 TWS 連線完成且帳戶列表已就緒時，對帳戶列表中的每一個帳戶向 TWS 請求帳戶市值與持倉，無需下游先發 `UPDATE_ACCOUNT_MV`。該輪結束後，若已指定 focus 帳戶且它不是本輪最後一個帳戶，系統 MUST 再訂閱該 focus 帳戶。

#### Scenario: 連線後寫入各帳戶 balance
- **WHEN** gateway 完成連線且帳戶列表已可用
- **THEN** 系統 MUST 向 TWS 請求列表中各帳戶的市值，並在收到回調後寫入對應的 `IBGateway:{account}:balance`

#### Scenario: 連線後輪詢結束訂回 focus 帳戶
- **WHEN** 連線後的市值刷新輪詢結束，且 focus 帳戶已指定、且不是本輪最後一個帳戶
- **THEN** 系統 MUST 再向 TWS 訂閱該 focus 帳戶

### Requirement: 每 30 秒嘗試再刷新市值
系統 SHALL 在連線存續期間，每 30 秒嘗試再跑一輪與連線後相同的帳戶市值刷新。未連線時 MUST 略過該次嘗試。上一輪刷新尚未結束時 MUST 略過該次嘗試，MUST NOT 並行向 TWS 發出第二輪帳戶市值請求。

#### Scenario: 連線中到期再刷新
- **WHEN** gateway 仍連線，距離上一輪開始已達 30 秒，且沒有進行中的市值刷新
- **THEN** 系統 MUST 再向 TWS 請求帳戶列表中各帳戶的市值

#### Scenario: 未連線時略過到期刷新
- **WHEN** 30 秒到期但 gateway 未連線
- **THEN** 系統 MUST NOT 向 TWS 發出帳戶市值請求

#### Scenario: 上一輪未完則略過
- **WHEN** 30 秒到期但上一輪帳戶市值刷新仍在進行
- **THEN** 系統 MUST NOT 再發出另一輪帳戶市值請求

### Requirement: 手動更新不得與定時刷新並行
系統 SHALL 在處理 Redis 指令 `UPDATE_ACCOUNT_MV` 時，與定時／連線後的市值刷新共用同一把互斥：兩路 MUST NOT 同時向 TWS 發出帳戶市值請求。指令格式與 ACK MUST 維持現狀。

#### Scenario: 定時刷新進行中的 UPDATE_ACCOUNT_MV 不並行
- **WHEN** 一輪帳戶市值刷新正在進行，且收到 `UPDATE_ACCOUNT_MV`
- **THEN** 系統 MUST NOT 同時再向 TWS 發出另一輪帳戶市值請求

### Requirement: 心跳不得承擔市值刷新
系統 SHALL 用獨立於秒級心跳的排程執行市值刷新。心跳寫入 `IBGateway:{name}:status` 的頻率 MUST NOT 因市值刷新輪詢（含帳戶間等待）而停頓。

#### Scenario: 市值輪詢期間心跳仍寫入
- **WHEN** 系統正在輪詢多個帳戶的市值
- **THEN** 秒級心跳 MUST 仍持續寫入 `IBGateway:{name}:status`
