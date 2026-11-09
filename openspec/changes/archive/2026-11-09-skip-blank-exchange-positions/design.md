## Context

見 proposal.md 的 Why。持倉訊息不帶 `exchange`，`ApiController.updatePortfolio` 只在 `primaryExch` 非 null 時把它抄到 `exchange`。`IBContract(Contract)` 在建構時就算好 `shownName`，`exchange()` 為 null 會變成字面量 `null:`。`AccountMVHandler` 的帳戶 map 只 `put`。`RM:IBKR` 每輪清空，且 `RmPair.positionKey` 在沒有交易所時用 `conid`，不走這條名字。

## Goals / Non-Goals

**Goals:**

- 空 `exchange` 的持倉能對上已知 `conid` 時，更新原上市交易所那筆。
- 對不上時不新增列。不刪已經在表裡的 `null:` key。記憶體裡已有的正常 key 留著。

**Non-Goals:**

- 不改每輪 End 才寫一次、不改 TTL、不改 `RM:IBKR` 的 key。
- 不在 End 用這一輪持倉整表取代。不從 Redis 把上一份 balance 讀回來合併。
- 不改 `src/main/java/com/ib/`，也不改合約明細寫進 Redis 的格式。

## Decisions

1. **用 `conid` 找已知合約，不用 `matchFullDetails`。** 後者在 `exchange` 為 null 時會略過交易所比對，可能對上別的合約。`conid` 為 0 不參與。同一 `conid` 有多筆時，先取非空且不是 `SMART` 的 `exchange`，否則取同樣條件的 `primaryExch`。只有 `SMART` 視為沒借到。
   - 替代：整筆略過、等下一輪。會讓這輪市價不更新。已知合約時應更新原 key。
   - 替代：借用後若只有 `SMART` 也寫入。會多出一筆與 `SEHK:` 不同的 key。

2. **補上交易所後重算 `shownName`。** 名字在 `IBContract` 建構時就固定。只改 `exchange()` 不會改已經算好的 `null:` 名字。補上市交易所後再跑一次與建構時相同的組名。

3. **仍空則不 `put`，但照常查明細。** 查詢用當下的 `shownName`（仍是 `null:`）。明細回來會以回應裡的交易所存進已知合約。下一輪同一 `conid` 就能借到。空交易所的合約不放進 `ibc_cache`，避免占住 `pair` 讓之後有交易所的那筆進不去。

4. **不掃已存在的 `null:` key。** 新的一輪不再 `put` 出 `null:`。這次事故留下的 key 在行程重啟時隨記憶體一起消失，不必在 `writePosition` 刪。`writePosition` 仍整表寫出，不清空。
   - 替代：寫入前刪掉 `null:` 前綴。可以不必重啟就去掉重複列，但這次不採用。
   - 替代：每輪 End 先清空該戶持倉再放入。壞的一輪會把好 key 一起清掉。

## Risks / Trade-offs

- [行程剛啟動且第一輪全部借不到] → 記憶體沒有舊持倉，這次 End 寫出的 balance 不含那些持倉，蓋過 Redis 上一份，直到下一輪有交易所的 End。與 proposal 的 Impact 相同。不讀回 Redis。
- [已知合約的交易所不是這次持倉真正的上市地] → 只接受非 `SMART` 的已知值；`conid` 對應的上市地在明細裡通常只有一個。
- [這次部署前記憶體裡已有 `null:` key] → 繼續跟正常 key 一起寫出，直到該行程重啟。新的空交易所回報不再新增這種 key。

## Migration Plan

部署後不改 Redis，也不掃舊的 `null:` key。重啟後的第一次 `accountDownloadEnd` 才不會再帶它們。回滾是還原 `AccountMVHandler`、`IBContract`、`ContractDetailsHandler` 這三處；已寫出的 balance 維持最後一次內容，不另做資料遷移。

## Open Questions

無。
