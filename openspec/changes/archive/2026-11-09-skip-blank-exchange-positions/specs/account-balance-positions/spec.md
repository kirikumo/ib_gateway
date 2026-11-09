# Spec Delta

## Purpose

讓 `IBGateway:{account}:balance` 的持倉列用上市交易所當 key，避免一輪缺少交易所的回報留下 `null:` 列，與後來的正常列並存。

## ADDED Requirements

### Requirement: 有交易所的持倉沿用該交易所
系統 SHALL 在持倉回報的 `exchange` 非空時，把該筆放在以這個 `exchange` 開頭的既有持倉 key 上。系統 MUST NOT 因本需求改寫已有交易所的 key、現金列、鍵名或 TTL。

#### Scenario: 港股持倉維持 SEHK key
- **WHEN** 持倉回報的 `exchange` 為 `SEHK`，標的為 `STK`、`HKD`、`7200`
- **THEN** 該戶 balance 的這筆 key MUST 為 `SEHK:STK:HKD-7200`

#### Scenario: SMART 不算空交易所
- **WHEN** 持倉回報的 `exchange` 為 `SMART`
- **THEN** 系統 MUST 保留以 `SMART` 開頭的既有 key，MUST NOT 改去借別的交易所

### Requirement: 空交易所時依 conid 借上市交易所
系統 SHALL 在持倉回報的 `exchange` 為 null 或空字串時，只用同一個非零 `conid` 向已知合約取上市交易所。取值 MUST 略過空字串與 `SMART`，並優先使用已知合約的 `exchange`，否則使用其 `primaryExch`。借到之後，這筆 MUST 更新到該交易所的持倉 key，MUST NOT 另建 `null:` key。

#### Scenario: 已知道 SEHK 的持倉更新原 key
- **WHEN** 持倉的 `exchange` 與 `primaryExch` 皆空，`conid` 與已知合約相同，且該合約的 `exchange` 為 `SEHK`
- **THEN** 這筆的數量與市值 MUST 寫入該 `SEHK` 持倉 key，balance MUST NOT 出現這筆的 `null:` key

#### Scenario: 只有 SMART 的已知合約不算借到
- **WHEN** 持倉的 `exchange` 為空，同 `conid` 的已知合約只有 `exchange` 為 `SMART` 且沒有非 `SMART` 的 `primaryExch`
- **THEN** 系統 MUST NOT 把這筆寫成 `SMART:` key，也 MUST NOT 建立 `null:` key

#### Scenario: conid 為 0 不對其他合約
- **WHEN** 持倉的 `exchange` 為空且 `conid` 為 0
- **THEN** 系統 MUST NOT 借用其他合約的交易所，也 MUST NOT 建立 `null:` key

### Requirement: 借不到就不放入這輪持倉
系統 SHALL 在持倉的 `exchange` 為空且借不到上市交易所時，不把這筆放入該戶 balance。系統 MUST NOT 因此移除該戶已有的其他持倉 key 或現金列。系統 MUST 仍允許這筆之後的合約明細進入已知合約，使下一輪仍無交易所的同一 `conid` 可以借到。

#### Scenario: 快取沒有這筆時略過
- **WHEN** 持倉的 `exchange` 為空，且沒有相同非零 `conid` 的已知上市交易所
- **THEN** 該戶 balance MUST NOT 新增這筆，已存在的其他持倉 key MUST 保持

#### Scenario: 明細回來後下一輪對上
- **WHEN** 上一輪因借不到而略過某 `conid`，其後已知合約有了該 `conid` 的非 `SMART` 交易所，下一輪同一持倉的 `exchange` 仍為空
- **THEN** 這一輪 MUST 把該筆寫入該交易所的持倉 key

### Requirement: 已存在的 null 持倉 key 留到重啟
系統 SHALL NOT 在寫入 `IBGateway:{account}:balance` 時移除已經在該戶表裡、key 以 `null:` 開頭的項目。這些項目隨行程重啟一併消失。系統 MUST NOT 為了處理它們而清空該戶整張表。

#### Scenario: 下一輪 End 保留先前的 null key
- **WHEN** 該戶 balance 已含 `null:STK:HKD-7200` 與 `SEHK:STK:HKD-7200`，且這次 `accountDownloadEnd` 寫入該戶
- **THEN** 寫入的內容 MUST 仍含 `null:STK:HKD-7200` 與 `SEHK:STK:HKD-7200`

#### Scenario: 整輪都沒有交易所時不抹掉舊持倉
- **WHEN** 這輪每一筆持倉的 `exchange` 都是空的且都借不到，該戶先前已有 `SEHK` 持倉 key，並且這次 `accountDownloadEnd` 寫入該戶
- **THEN** 寫入的內容 MUST 仍含那些 `SEHK` 持倉 key，MUST NOT 只剩現金列
