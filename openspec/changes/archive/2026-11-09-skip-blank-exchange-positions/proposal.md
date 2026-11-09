## Why

2026-11-05 06:07 TWS 與 IBKR 斷線（1100），06:08 恢復（1102，data maintained）後，`DU166679` 的一整輪 `updatePortfolio` 共 126 檔都沒有 `primaryExch`。持倉訊息本身沒有 `exchange`，名字被拼成 `null:STK:HKD-7200` 這類 key，寫進 `IBGateway:{account}:balance` 後不會刪。下一輪 IB 又帶回 `SEHK:` / `NASDAQ:`，壞 key 與正常 key 並存，下游加總會把同一持倉算兩次，直到行程重啟。

## What Changes

- 持倉的 `exchange` 為空時，用同一個 `conid` 向已知道的合約借上市交易所，略過 `SMART`，把這筆更新到原本的 `SEHK:STK:...` 這類 key。
- 借不到就不放入該戶 balance。不建立新的 `null:` key。仍可查合約明細，讓下一輪對得上。
- 已經在表裡的 `null:` key 不主動刪。行程重啟後記憶體是空的，它們自然不在。
- 不在每輪 End 清空整張表。有交易所的 key、現金列、鍵名、TTL、寫入時機都維持現狀。`RM:IBKR:{account}:balance` 的 key 規則不改。

## Capabilities

### New Capabilities

- `account-balance-positions`: `IBGateway:{account}:balance` 裡的持倉列用上市交易所當 key；沒有交易所且快取對不到 `conid` 時不寫入。已存在的 `null:` key 留到重啟。

### Modified Capabilities

## Impact

- `AccountMVHandler.updatePortfolioBody` 與 `writePosition`。現在 `shownName()` 在 `exchange()` 為 null 時會得到字面量 `null:`，而且 map 只 `put` 不刪。
- `ContractDetailsHandler.KNOWN_CONTRACTS` 只讀，用來依 `conid` 取交易所。合約 Redis 的寫入格式不改。
- `IBContract` 需要在補上交易所後重算 `shownName`。不改 `src/main/java/com/ib/`。
- 這次部署前已經留下的 `null:` key，要等該行程重啟才會從 balance 消失。正常 key 的名稱與 JSON 形狀不變。
- 行程剛啟動、快取是空的、而且第一輪持倉也沒有交易所時，這些持倉要等下一輪有交易所或明細進快取後才出現。這次事故的下一輪（約 30 秒）已經有交易所。
