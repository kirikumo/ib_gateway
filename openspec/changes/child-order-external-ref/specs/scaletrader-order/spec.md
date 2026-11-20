# Spec Delta

## MODIFIED Requirements

### Requirement: 衍生止盈子單唯一鍵位派生與母子層級關聯
系統 SHALL 在接收到由 ScaleTrader 或其他演算法派生之子單（`parentId != 0 || parentPermId != 0`）時，以母單原始 `orderRef` 與子單 `permId` 組成對外鍵 `${parentOrderRef}@${childPermId}`。此鍵用於 OMS、訂單快取與 `RM:IBKR:{account}:open`。送給 IBKR 的委託 `orderRef` MUST 維持券商原來的字串。`permId` 尚未大於 0 時，系統 MUST NOT 寫出該子單的對外鍵，也 MUST NOT 把該子單記在母單鍵上。

#### Scenario: 止盈子單唯一鍵位派生
- **WHEN** 系統收到關聯父單的衍生子單且其 `permId` 大於 0
- **THEN** 該子單對外的 `orderRef`、`client_oid` 與 `i` MUST 為 `${parentOrderRef}@${childPermId}`，且不得重複追加後綴
- **AND** 送給 IBKR 的委託 `orderRef` MUST 仍是母單原始 `orderRef`

#### Scenario: 多個止盈子單並存不衝突
- **WHEN** 同一個母單先後或同時派生多個具備不同 `permId` 的止盈子單
- **THEN** 各子單在 Redis 哈希表中 MUST 以各自的 `${parentOrderRef}@${childPermId}` 分別儲存，且母單記錄 MUST 保持完整

#### Scenario: 父子層級關聯欄位輸出
- **WHEN** 系統序列化衍生子單的 OMS JSON
- **THEN** JSON 內 MUST 包含 `parentId` 與非零的 `parentPermId`；策略端可由 `orderRef`（`${parentOrderRef}@${childPermId}`）直接解析母單標識符，故 JSON 內不重複輸出 `parentOrderRef`；常規非衍生訂單 MUST NOT 包含上述欄位

#### Scenario: permId 尚未取得
- **WHEN** 衍生子單的 `permId` 仍是 0
- **THEN** 系統 MUST NOT 寫出該子單的對外鍵，母單在 OMS、訂單快取與 `RM:IBKR:{account}:open` 的記錄 MUST 保持不變

#### Scenario: 子單送回 IBKR
- **WHEN** 系統把一張已有對外鍵的衍生子單送回 IBKR
- **THEN** 請求中的 `orderRef` MUST 是券商原來的字串，對外鍵 MUST 仍是 `${parentOrderRef}@${childPermId}`

## ADDED Requirements

### Requirement: 重連先收訂單快照再要成交
重連後，系統 MUST 先要求未完成單與當日完結單。兩個結束信號都到了之後，系統 MUST 才要求當日成交，且這次連線只要求一次。系統 MUST NOT 在這兩個結束信號之前要求成交。`resetStatus()` MUST 清掉已要求的標記。OMS 初始化仍在未完成單結束時進行，MUST NOT 等成交結束。

風控對帳 MUST 等未完成單結束、當日完結單結束，以及這次成交回報結束，才處理殘留的 `open` field。手動 `REQ_EXECUTIONS` MUST NOT 觸發這次對帳。

已在快取或正在處理的訂單，成交 MUST 用該訂單的對外鍵。衍生子單的對外鍵是 `${parentOrderRef}@${childPermId}`。系統還沒有該訂單時，成交 MUST 使用成交訊息上的 `orderRef`。系統 MUST NOT 把這筆成交掛起，也 MUST NOT 為它另造鍵。

#### Scenario: 兩個訂單快照都結束才要成交
- **WHEN** 未完成單與當日完結單都已結束
- **THEN** 系統 MUST 要求當日成交，且這次連線只要求一次

#### Scenario: 只到其中一個訂單結束
- **WHEN** 只有未完成單結束，或只有當日完結單結束
- **THEN** 系統 MUST NOT 要求成交，也 MUST NOT 把殘留 `open` 推成 `removed`

#### Scenario: 成交回報尚未結束
- **WHEN** 兩個訂單快照都已結束，但這次成交回報尚未結束
- **THEN** 系統 MUST NOT 把殘留 `open` 推成 `removed`

#### Scenario: 成交回報結束後對帳
- **WHEN** 未完成單結束、當日完結單結束，且這次成交回報結束
- **THEN** 系統 MUST 用當日完結單與當日成交對殘留 `open`

#### Scenario: 訂單已在快取
- **WHEN** 一筆成交到達，且該 `permId` 的衍生子單已在快取或正在處理
- **THEN** 成交 MUST 以 `${parentOrderRef}@${childPermId}` 寫出

#### Scenario: 即時成交先於子單
- **WHEN** 連線期間一筆成交先於該子單的 `openOrder` 到達
- **THEN** 該筆成交的 `orderRef` MUST 是成交訊息上的 `orderRef`
- **AND** 系統 MUST NOT 另造鍵，也 MUST NOT 把這筆成交掛起
