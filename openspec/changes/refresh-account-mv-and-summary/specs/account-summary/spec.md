## Purpose

定義帳戶摘要如何向 TWS 取值，以及 Redis `IBGateway:Summary:{account}` 何時刷新，讓第一次 snapshot 之後的增量更新也反映在下游讀到的摘要。

## ADDED Requirements

### Requirement: 初始 snapshot 結束後寫入 Redis
系統 SHALL 在帳戶摘要請求的初始 snapshot 結束時，把各帳戶摘要寫入 Redis key `IBGateway:Summary:{account}`。寫入內容 MUST 含該帳戶當下的摘要欄位與此次寫入時間 `updateTime`。

#### Scenario: 連線後第一份摘要寫入 Redis
- **WHEN** gateway 完成連線並完成一次帳戶摘要 snapshot
- **THEN** 對應帳戶的 `IBGateway:Summary:{account}` MUST 被寫入，且 `updateTime` MUST 為此次寫入時間

### Requirement: 增量更新也寫入該帳戶 Redis
系統 SHALL 在初始 snapshot 結束之後，於收到該帳戶摘要欄位的後續更新時，把該帳戶當下完整摘要寫入 `IBGateway:Summary:{account}`，MUST NOT 等到下一次 snapshot 結束。`updateTime` MUST 為此次寫入時間。初始 snapshot 結束之前，系統 MUST NOT 因單一欄位回調而寫入不完整的摘要。

#### Scenario: snapshot 結束後的欄位更新寫入 Redis
- **WHEN** 初始 snapshot 已結束，且系統收到某帳戶一個已變更的摘要欄位
- **THEN** 該帳戶的 `IBGateway:Summary:{account}` MUST 被寫入，內容 MUST 含該新欄位值，且 `updateTime` MUST 為此次寫入時間

#### Scenario: snapshot 結束前不寫半套摘要
- **WHEN** 系統正在接收某次帳戶摘要請求的初始 snapshot，且尚未結束
- **THEN** 系統 MUST NOT 把該次尚未結束的不完整摘要寫入 Redis

### Requirement: 新的 snapshot 在結束前不得當增量寫入
系統 SHALL 在開始一次新的帳戶摘要請求時，把「初始 snapshot 已結束」狀態清掉，直到該次 snapshot 結束。重連後的第一次 snapshot MUST 遵守此規則。

#### Scenario: 重連後第一輪 snapshot 不提前寫 Redis
- **WHEN** gateway 斷線後再連線並送出新的帳戶摘要請求
- **THEN** 在該次 snapshot 結束之前，系統 MUST NOT 把不完整摘要寫入 Redis；結束後 MUST 寫入完整摘要

### Requirement: FIND_ACCOUNT_SUMMARY 已訂閱時略過重送
系統 SHALL 在已有有效帳戶摘要訂閱時，對 Redis 指令 `FIND_ACCOUNT_SUMMARY` 略過向 TWS 重送請求，以避免超過同時兩個摘要訂閱的限制。該指令 MUST 仍送出 ACK。

#### Scenario: 已有摘要時 FIND 略過重送
- **WHEN** 系統已有有效的帳戶摘要訂閱，且收到 `FIND_ACCOUNT_SUMMARY`
- **THEN** 系統 MUST NOT 向 TWS 再送出新的帳戶摘要請求，且該指令 MUST 仍收到 ACK

### Requirement: 連線後自動查詢一次
系統 SHALL 在與 TWS 連線完成且帳戶列表已就緒時，自動送出一次帳戶摘要請求，無需下游先發 `FIND_ACCOUNT_SUMMARY`。本次 change 不定時重送該請求。

#### Scenario: 連線後請求帳戶摘要
- **WHEN** gateway 完成連線且帳戶列表已可用
- **THEN** 系統 MUST 向 TWS 請求帳戶摘要
