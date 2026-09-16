## Purpose

讓對外 top／depth 張數反映 IB 行情本身的數量，不再用下單建議步進去縮放盤口。

## ADDED Requirements

### Requirement: 行情張數不得用 suggestedSizeIncrement 縮放
系統 SHALL NOT 用合約細節的 `suggestedSizeIncrement` 乘上 top 或 depth 的對外張數 `s`。該欄位 MUST 只作為下單數量規則寫入 Redis，MUST NOT 當行情 lot 倍率。

#### Scenario: 非港股 top 張數不乘建議步進
- **WHEN** 系統訂閱非 SEHK／HKFE 合約的頂層行情，且 Redis 中該合約 `suggestedSizeIncrement` 為 `100`
- **THEN** 對外發布的買賣張數 `s` MUST NOT 等於 IB `tickSize` 再乘 `100`

#### Scenario: suggestedSizeIncrement 為 0 時仍發布 IB 張數
- **WHEN** 系統訂閱 IDEALPRO CASH 頂層行情，且 `suggestedSizeIncrement` 為 `0` 或 `null`
- **THEN** 對外發布的張數 `s` MUST 依 IB 回傳張數計算，MUST NOT 因該欄位為 0 而變成 0

#### Scenario: SEHK 張數行為不變
- **WHEN** 系統訂閱 SEHK 股票的頂層或深度行情
- **THEN** 對外張數 MUST 仍以 IB 回傳張數為準，MUST NOT 改為乘上 `suggestedSizeIncrement`

### Requirement: 重訂行情沿用同一規則
系統 SHALL 在重訂已存在的 top／depth 訂閱時，套用與初次訂閱相同的張數規則，MUST NOT 把舊的 `suggestedSizeIncrement` 倍率帶回新訂閱。

#### Scenario: 重訂後不再乘建議步進
- **WHEN** 系統因連線重置重訂一筆非 SEHK 的 depth 訂閱
- **THEN** 重訂後對外張數 MUST NOT 使用 `suggestedSizeIncrement` 作為乘數
