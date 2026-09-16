## Context

見 `proposal.md` 的 Why。現況約束：

- `ContractDetailsHandler.writeDetail` 寫 `IBGateway:Contract:...`（TTL 2764800），`detailToJSONObject` 寫 `FIND_CONTRACTS_TO_REDIS` 的列表（TTL 300）。兩處對 size 三欄都呼叫 `Decimal.longValue()`。
- `Decimal.INVALID` / `NaN` 的 `isValid()` 為 false；`INVALID.longValue()` 卻是 `Long.MAX_VALUE`。`null` 呼叫 `longValue()` 會 NPE。
- `Decimal.ZERO` 是有效值。`Decimal` 內部 scale 固定 16，`toString()` 會 `stripTrailingZeros().toPlainString()`。
- Redis 序列化走 Fastjson `JSON.toJSONString`。`minTick` 已是 JSON number（double）。
- 舊 `mdSizeMultiplier` 在 `EDecoder` 已標 `not used anymore`。訂閱 top／depth 卻把 `suggestedSizeIncrement` 當成 `marketDataSizeMultiplier`。
- 張數現況：SEHK／HKFE 只用 IB 張數（IBOND 另有 /10）；期權 `* 1.0`；其他市場 `IB張數 * 合約multiplier * suggestedSizeIncrement`，乘積為 0 則 fallback 成 `IB張數 * multiplier`。

## Goals / Non-Goals

**Goals:**

- size 三欄寫出可含小數的 JSON number，語意對齊 `minTick`。
- 未填 / invalid / null 寫 JSON `null`，有效 `0` 寫數字 `0`。
- 兩條寫入路徑共用同一套編碼，避免再複製貼上出錯。
- 行情張數不再讀 `suggestedSizeIncrement`；該欄只當 Redis 數量規則。

**Non-Goals:**

- 不改下單、`cashQty`、OMS 數量、10318。
- 不改 SEHK／HKFE 既有張數路徑（含 IBOND /10）。
- 不改期權已是 `* 1.0` 的路徑。
- 不在本次把 `tickSize` 的 `longValue()` 改成保留小數成交張（IB 10.44 LAST_SIZE）；那是另一個精度問題。
- 不把 `writeDetail` 與 `detailToJSONObject` 整段合併重構（只抽出 size 編碼）。
- 不主動掃描／改寫已在 Redis 的舊 key。

## Decisions

### 1. JSON number 用 `BigDecimal`，不用 `long`、不用字串

- **選擇**：有效 `Decimal` 寫入 `value().stripTrailingZeros()` 的 `BigDecimal`；Fastjson 序列化成 JSON number。整數 `1` 仍是 `1`，小數 `0.01` 是 `0.01`。
- **理由**：與既有 `minTick` 同為數字，下游 `getDoubleValue` 可用。字串會讓現有數字解析壞得更徹底；`doubleValue()` 有二進位誤差；`longValue()` 正是這次的 bug。
- **替代**：字串 `toPlainString()` → 否決，BREAKING 面比 number 大。`double` → 否決，size 步進應保持十進位。

未填編碼：

```
null 或 !isValid() -> JSON null
有效 Decimal      -> stripTrailingZeros 的 BigDecimal
```

`INVALID` 的內部值是 `Long.MIN_VALUE`，MUST 走 `isValid()` 過濾，不得把該 BigDecimal 寫出去。

### 2. 抽出共用編碼，兩處呼叫

- **選擇**：在 `ContractDetailsHandler` 加私有方法，輸入 `Decimal`、輸出 `BigDecimal` 或 `null`；`writeDetail` 與 `detailToJSONObject` 的三個 size 欄位都改走它。
- **理由**：兩方法已標「direct copy」，只改一處會漏。不在本次合併整份 JSON 組裝。
- **替代**：只改 `writeDetail` → 否決，`FIND_CONTRACTS_TO_REDIS` 仍失真。

### 3. 行情停止用 suggestedSizeIncrement 當倍率

- **選擇**：`GatewayController` 訂閱／重訂 top、depth 不再 `getLongValue("suggestedSizeIncrement")`。非 SEHK 張數改為 `IB張數 * 合約multiplier`（合約無 multiplier 則為 1）。SEHK／HKFE 與期權維持現況。`suggestedSizeIncrement` 只經 `ContractDetailsHandler` 寫入 Redis。
- **理由**：IB 定義是下單建議步進，不是 `mdSizeMultiplier`。TWS 985 後美股 size 已是股數；乘 100 會放大盤口。期權與港股已經不乘這個欄位。CASH 的 0 只是靠 fallback 才沒把 `s` 乘成 0。
- **替代**：Redis 寫原值、讀取仍 `getLongValue` → 否決，bug 留著。改成 `getDoubleValue` 當倍率 → 否決，用錯欄位用得更認真。

實作形狀：訂閱端傳入的 sizeMultiplier 固定為 `1`，或刪除該參數並讓 Top／Deep 不再乘它。重訂必須用同一規則，不得把 handler 裡舊倍率帶回去。

乘積為 0 的 fallback 可留：只在 IB 張數本身為 0 時還有意義，不再承擔「建議步進是 0」的補償。

## Risks / Trade-offs

- [下游假設 size 一定是整數] → 緩解：proposal 標 **BREAKING**；SOP 註明可為小數或 null。整數合約 JSON 仍是不帶小數點的 number。
- [舊 Redis key 仍是截斷後的 0，TTL 最長約 32 天] → 緩解：佈署後對目標合約再 `FIND_CONTRACTS`；不寫 migration job。
- [Fastjson 把某些 BigDecimal 寫成科學記號] → 緩解：`stripTrailingZeros()` 後一般為普通十進位；驗證序列化 `0.01` / `1` / `0`。
- [非 SEHK 對外 `s` 若舊倍率為 100 會縮小] → 緩解：標 **BREAKING**；這是修用錯欄位。港股／期權不變。佈署後對照 TWS 盤口抽樣美股。
- [重訂仍用 handler 快取的舊倍率] → 緩解：重訂與初訂走同一條「不再讀 suggestedSizeIncrement」路徑。

## Migration Plan

- 佈署並重啟 gateway（行情 handler 的倍率在建構時定死）。
- 需要新合約 JSON 的標的發一次 `FIND_CONTRACTS`（或等下次自動 query）。
- 回滾：還原 `ContractDetailsHandler` 與行情訂閱／張數計算並重啟。
- 驗證：合約 JSON 的 `0.01`／`1`／`0`／null；非 SEHK top `s` 不隨 `suggestedSizeIncrement=100` 放大；SEHK 與期權張數與改前一致；CASH `suggestedSizeIncrement=0` 時 `s` 不為 0。
