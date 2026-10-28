## Purpose

定義舊的帳戶餘額鍵何時寫入 Redis，讓下游讀到的是該戶這一輪收完的內容，而不是快照寫到一半的半套。

## ADDED Requirements

### Requirement: 只在該戶 download end 寫一次
系統 SHALL 只在該戶這輪 `accountDownloadEnd` 寫入 `IBGateway:{account}:balance`。一次 End MUST 寫該戶恰好一次，且只寫該戶。快照進行中的現金或持倉回呼 MUST NOT 寫這個鍵。鍵名、TTL 與 JSON 形狀 MUST 與變更前相同。

#### Scenario: 初始化之後連續多筆持倉
- **WHEN** 帳戶已經完成過至少一次下載，且這輪在 End 之前收到多筆持倉或現金回呼
- **THEN** 這些回呼 MUST NOT 寫入 `IBGateway:{account}:balance`，該戶 `accountDownloadEnd` MUST 寫入恰好一次

#### Scenario: 第一輪在 End 前
- **WHEN** 程序啟動後的第一輪尚未收到該戶 `accountDownloadEnd`
- **THEN** 系統 MUST NOT 寫入該戶的 `IBGateway:{account}:balance`

#### Scenario: 別戶的 End 不重寫這一戶
- **WHEN** 帳戶 A 的餘額鍵已經寫入，接著帳戶 B 的 `accountDownloadEnd` 到達
- **THEN** 系統 MUST NOT 因 B 的 End 再寫 `IBGateway:A:balance`

### Requirement: 沒有 End 就留著上一份
這輪沒有 `accountDownloadEnd`（逾時或斷線）時，系統 MUST NOT 改寫該戶的 `IBGateway:{account}:balance`。上一份已寫入的內容 MUST 留著。

#### Scenario: 回呼開始之後沒有 End
- **WHEN** 這輪已經收到部分持倉回呼，但沒有該戶的 `accountDownloadEnd`
- **THEN** `IBGateway:{account}:balance` MUST 仍是上一份已寫入的內容

### Requirement: AccountReady 為 false 仍寫舊鍵一次
`AccountReady=false` 的 End MUST 仍寫該戶 `IBGateway:{account}:balance` 一次。系統 MUST NOT 把 `RM:IBKR` 餘額與摘要的可寫條件套到這個舊鍵上。此情況下 `RM:IBKR:{account}:balance` 與 `RM:IBKR:{account}:summary` MUST NOT 更新。

#### Scenario: AccountReady 為 false 仍收到 End
- **WHEN** 這輪出現 `AccountReady=false`，其後收到該戶 `accountDownloadEnd`
- **THEN** `IBGateway:{account}:balance` MUST 被寫入一次，且 `RM:IBKR:{account}:balance` 與 `RM:IBKR:{account}:summary` MUST NOT 更新

### Requirement: 快照日誌只留 End 那一行
每一戶的 `accountDownloadEnd` MUST 留下一行能辨認該戶的日誌。逐筆持倉、逐筆現金，以及每一次 balance 寫入，MUST NOT 各印一行日誌。

#### Scenario: 一百檔持倉的一輪快照
- **WHEN** 某戶這輪在 End 前收到多筆持倉回呼，然後收到 `accountDownloadEnd`
- **THEN** 日誌 MUST 有一行能辨認該戶的 End，且 MUST NOT 為每一筆持倉或每一次 balance 寫入各印一行
