# contract-details-cache Specification

## Purpose

讓下游從 Redis 合約快取讀到 IB 回傳的數量規則原值，能分辨未填與真實零，且小數步進不會被截成整數。`suggestedSizeIncrement` 在此僅作為下單建議步進寫入，行情張數行為見 `market-data-size`。

## Requirements

### Requirement: 數量規則欄位保留 IB 原值
系統 SHALL 在寫入 Redis 的合約 JSON 中，將 `minSize`、`sizeIncrement`、`suggestedSizeIncrement` 存成 JSON number，並保留 IB 回報的數值（含小數）。系統 MUST NOT 把這三個欄位強制轉成整數。

#### Scenario: 小數 minSize 不被截成 0
- **WHEN** IB 對某合約回報有效的 `minSize` 為 `0.01`
- **THEN** Redis 合約 JSON 的 `minSize` MUST 為數字 `0.01`，MUST NOT 為 `0`

#### Scenario: 整數 minSize 仍為數字 1
- **WHEN** IB 對某合約回報有效的 `minSize` 為 `1`
- **THEN** Redis 合約 JSON 的 `minSize` MUST 為數字 `1`

#### Scenario: 有效的 0 與未填可區分
- **WHEN** IB 對某合約回報有效的 `minSize` 為 `0`
- **THEN** Redis 合約 JSON 的 `minSize` MUST 為數字 `0`，MUST NOT 為 `null`

### Requirement: 未填或 invalid 的數量規則為 null
系統 SHALL 在 IB 未提供、值為 null、或值為 invalid 時，將對應的 `minSize`、`sizeIncrement`、`suggestedSizeIncrement` 寫成 JSON `null`。系統 MUST NOT 用 `0` 或極大整數代替未填。

#### Scenario: 未填 minSize 寫成 null
- **WHEN** IB 未提供有效的 `minSize`
- **THEN** Redis 合約 JSON 的 `minSize` MUST 為 `null`，MUST NOT 為 `0`

#### Scenario: invalid sizeIncrement 寫成 null
- **WHEN** IB 回報的 `sizeIncrement` 為 invalid
- **THEN** Redis 合約 JSON 的 `sizeIncrement` MUST 為 `null`

### Requirement: 兩條合約寫入路徑使用相同編碼
系統 SHALL 對單筆合約快取與依請求寫出的合約列表，套用相同的 `minSize`、`sizeIncrement`、`suggestedSizeIncrement` 編碼規則。

#### Scenario: 單筆快取與列表結果一致
- **WHEN** 同一份 IB 合約細節分別寫入單筆合約 key 與請求結果列表
- **THEN** 兩處對應物件的 `minSize`、`sizeIncrement`、`suggestedSizeIncrement` MUST 相同
