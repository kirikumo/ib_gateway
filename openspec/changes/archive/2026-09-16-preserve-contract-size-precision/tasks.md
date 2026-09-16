## 1. Size 欄位編碼

- [x] 1.1 在 `ContractDetailsHandler` 抽出共用編碼：`null` 或 `!Decimal.isValid()` 回 `null`，有效值回 `value().stripTrailingZeros()` 的 `BigDecimal`。`mvn test` 覆蓋 `0.01`、`1`、`0`、`null`、`Decimal.INVALID`、`Decimal.NaN`；Fastjson `JSON.toJSONString` 對 `0.01` 為數字 `0.01`（不是 `0`），對 invalid／null 為 `null`（不是 `0` 或 `Long.MAX_VALUE`）。

## 2. 兩條寫入路徑

- [x] 2.1 `writeDetail` 與 `detailToJSONObject` 的 `minSize`、`sizeIncrement`、`suggestedSizeIncrement` 都改走該編碼。`grep` 這兩個方法內不得再對這三欄呼叫 `longValue()`；同一份 `ContractDetails` 經兩路徑組出的 JSON 這三欄必須相同。

## 3. 行情張數不再乘建議步進

- [x] 3.1 `GatewayController` 訂閱與重訂 top／depth 不再 `getLongValue("suggestedSizeIncrement")` 當倍率。`grep` `GatewayController.java` 不得再把該欄位傳進 handler。
- [x] 3.2 `TopMktDataHandler`／`DeepMktDataHandler` 的對外 `s` 不再乘 `suggestedSizeIncrement`；非 SEHK 為 IB 張數 × 合約 multiplier（無則 1）；SEHK／HKFE 與期權維持現況。單元測試：`suggestedSizeIncrement=100` 時 `s` 不放大；`=0` 或 null 時 `s` 不為 0；SEHK 與改前相同。

## 4. 文件與回歸

- [x] 4.1 更新 `doc/ibkr_place_order_SOP.md`：`minSize`／`sizeIncrement`／`suggestedSizeIncrement` 可為小數或 `null`；`0` 是有效零；`suggestedSizeIncrement` 不是行情倍率。
- [x] 4.2 `mvn test` 通過。
