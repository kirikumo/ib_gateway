## Context

見 `proposal.md` 的 Why。和這次做法有關的現況：

- `AccountMVHandler` 在第一次 `accountDownloadEnd` 之前把回呼積在 `_tmpData`，End 才併進 `_data` 並寫 Redis。`_dataInit` 之後，每一筆現金或持倉回呼都改 `_data` 並呼叫 `writePosition()`。`writePosition()` 會把 `_data` 裡每個帳戶的整包再 `SETEX` 一次。
- `RmAccountFeed` 已經只在該戶 End 寫 `RM:IBKR:{account}:balance` 與 `summary`，而且 `AccountReady=false` 或不完整的一輪不寫。舊鍵沒有跟著這個時機。
- `findDetails()` 每次都先 `queryDetails()`。冷卻是 10 秒，帳戶輪詢是 30 秒，所以下一輪同一批合約全部重查。成功結果在 `KNOWN_CONTRACT_DETAILS`，JSON 有 `_timestamp`。回呼裡的 `writeDetail` 才寫 `IBGateway:Contract:{名稱}`，TTL 2764800 秒（32 天）。
- `MarketRuleHandler` 已有記憶體快取，命中就不再向 IB 查，也不重寫 Redis。這次不動它。
- `queryContractListToRedis` 不經 `findDetails`。`queryContractListWithCache`、下單與持倉回呼都經 `findDetails`。

## Goals / Non-Goals

**Goals:**

- 新鮮快取命中時不打 IB、不重寫合約 Redis、不印查詢日誌。
- 舊的 `IBGateway:{account}:balance` 改成該戶 End 寫一次，只寫該戶。
- 既有測試能鎖住「回呼不寫舊鍵、End 寫一次、`AccountReady=false` 仍寫舊鍵且不寫 RM」。
- 當天日檔路徑不見或被換成另一個檔時，下一行寫回原路徑。

**Non-Goals:**

- 不改 30 秒輪詢、不改 `RmAccountFeed` 的可寫條件、不改 `com.ib/`。
- 不把帳戶快照從「併進既有 map」改成「整本替換」。
- 不加 log level，不改日檔保留天數。當天檔被刪掉時要能再出現，保留天數本身不變。
- 不讓 market rule 的 Redis TTL 跟著合約刷新重寫。那是既有行為：規則命中快取就直接返回。

## Decisions

### 1. 合約以成功時間算 24 小時，命中就整段返回

- **選擇**：`queryDetails` 一開始用合約名稱看 `KNOWN_CONTRACT_DETAILS`。有 `_timestamp` 且 `now < _timestamp + 24h` 則立刻返回，不印日誌、不碰 `QUERY_HIS`、不呼叫 `queryContractList`。沒有這筆、沒有 `_timestamp`、或已滿 24 小時，才走現有的 10 秒冷卻與 SMART／combo 連帶查詢。24 小時用一個常數，測試讀得到。
- **理由**：現場是同一秒內 110 檔、每 30 秒再來一輪。10 秒冷卻擋不住 30 秒週期。成功才寫入的 `_timestamp` 比「送出查詢的時間」準，刷新失敗時讀取端仍留著上一份。24 小時遠長於 30 秒，又短於 32 天的 Redis TTL，長駐程序不會把合約鍵放過期。重啟後記憶體是空的，第一輪仍會整批查一次。
- **替代**：只在程序啟動查一次 → 否決（程序連續跑超過 32 天時合約鍵 TTL 到期）。把冷卻從 10 秒改成 24 小時但不看成功快取 → 否決（查詢失敗也會被鎖 24 小時）。每次持倉都查、只把日誌拿掉 → 否決（IB 與 Redis 的重寫還在）。

連帶查詢跟著這次判斷。名稱仍新鮮時，SMART 那次「交易所改成 null」的第二次 `reqContractDetails` 也不送。名稱不新鮮或還沒有時，兩次查詢都維持現況。

`queryContractListToRedis` 維持每次打 IB。它是明示要寫進某次請求結果的路徑，不吃這份 24 小時快取。

回呼裡的「contract detail」與「>>> Redis IBGateway:Contract:」日誌留著。不再每 30 秒查之後，它們只出現在第一次與滿 24 小時的刷新。

### 2. 舊餘額鍵只在該戶 End 寫該戶

- **選擇**：現金與持倉回呼只改記憶體。`accountDownloadEnd(account)` 才寫 `IBGateway:{account}:balance`，而且只寫這個 `account`。`_data` 沒有該戶就不寫。TTL 仍是 2764800，值仍是該戶當下的 map。`RmAccountFeed.onDownloadEnd` 仍排在這次寫入之後。
- **理由**：現在每筆持倉都把三個帳戶整包重寫，快照寫到一半時 Redis 上是半套。End 寫一次的內容與「這一輪最後一筆回呼會寫出的那份」相同，只是少寫一百多次。只寫該戶，避免 B 的 End 把 A 的 TTL 與內容再刷一次。`AccountReady=false` 不拿來擋舊鍵：RM 維持不寫，舊鍵在這次 End 仍寫一次，與 `AccountMVHandlerRmTest` 裡這條案例的方向一致。
- **替代**：End 時沿用現在的 `writePosition()`，把 `_data` 裡所有帳戶都寫一遍 → 否決（B 的 End 會重寫 A）。舊鍵也套用 RM 的 `AccountReady`／`NetLiquidation` 條件 → 否決（proposal 明確把這組條件留在 RM）。回呼仍寫 Redis、只刪日誌 → 否決。

```
updatePortfolio / CashBalance
        |
        v
   只改 _data 或 _tmpData
        |
        v
accountDownloadEnd(account)
        |
        +-- 第一輪：_tmpData 併進 _data
        +-- 寫 IBGateway:{account}:balance 一次
        +-- rmFeed.onDownloadEnd(account)   （RM 條件不變）
```

沒有 End 的一輪（`RmAccountCycle` 逾時或斷線）不呼叫 `accountDownloadEnd`，因此不寫舊鍵。

### 3. 熱路徑日誌

- **選擇**：拿掉持倉回呼那行、現金回呼那行，以及 balance 寫入的「>>> Redis」。`queryDetails` 只在真正送出查詢時留一行 warn。`accountDownloadEnd` 現有的那行 End 日誌保留，作為該戶這輪的那一行。
- **理由**：規格要求一百檔的一輪只有 End 能辨認，而不是每一檔一行。真正的新查詢與每日刷新仍要在日誌裡看得到。
- **替代**：留著逐筆持倉、只靠日檔輪替 → 否決（那就是現在的 13MB／小時）。

### 4. 測試不碰 Redis、不建 GatewayController

- **選擇**：合約測試放在 `com.avalok.ib.handler`，直接準備 `KNOWN_CONTRACT_DETAILS` 再呼叫 `queryDetails`。新鮮時斷言 `QUERY_HIS` 沒有該鍵，而且在 `GW_CONTROLLER` 為 null 時也不會去呼叫它。過期與缺少 `_timestamp` 走「需要查」的判斷；需要查的路徑在測試裡不接上真正的 controller。餘額測試繼續用 `setBalanceWriter`。`new GatewayController()` 會建立 `RedisRmStore`，測試不得這樣做。
- **理由**：`AccountMVHandlerRmTest` 已經用 writer 替身鎖住舊鍵與 RM 的差別。合約這次的分支在碰到 controller 之前就返回，不必再做一個 controller 替身。
- **替代**：抽出新的查詢介面只為了測試 → 這次的早期返回用不到。

`AccountMVHandlerRmTest.successfulEndSharesTimestampAndIncrementalPortfolioKeepsRm` 現在斷言初始化之後的 `updatePortfolio` 會再寫一次舊鍵。改成：那筆回呼不增加寫入；下一次該戶 `accountDownloadEnd` 才再寫一次。`portfolioBeforeEndWritesNeitherKey` 與 `accountReadyFalseEndKeepsPreviousRmAndStillWritesOldBalance` 的預期維持。補一則：A 已寫入之後，B 的 End 只多出 B 的鍵。

### 5. 當天日檔路徑不見或被換掉時重開

- **選擇**：`ensureOpen` 在沿用現有 writer 之前，比較路徑上的檔與已打開的檔。路徑不存在，或 `fileKey` 對不上，就 `closeWriter()`，再走現有的建立目錄、`CREATE` + `APPEND`、清理舊日期。`fileKey` 不可用時，只看路徑是否存在。只清空同一個檔時 `fileKey` 不變，不重開。沒有新的一行就不建檔。
- **理由**：Linux 上刪掉已打開的檔只拿掉目錄名，writer 仍寫進舊 inode，名字不會自己回來。只看「檔是否存在」會漏掉一種情況：刪掉之後、下一行之前，同名路徑上已經有另一個檔，exists 為真，輸出卻繼續進看不見的舊 inode。關掉舊 writer 才釋放那個 inode 的空間。
- **替代**：每次寫入都關掉再打開 → 否決（沒有刪檔時不需要）。只在寫入丟出例外時重開 → 否決（刪掉已打開的檔，寫入在 Linux 上不會失敗）。換日或重啟才再建 → 否決（這就是現況）。

## Risks / Trade-offs

- [合約屬性在 24 小時內變了] → 接受。交易所規則與乘數很少在日內改。重啟會立刻重查；長駐則最久 24 小時。明示的 `queryContractListToRedis` 仍可隨時打 IB。
- [逾時的一輪已把持倉併進記憶體，Redis 仍是上一份] → 接受。下一輪成功的 End 會把併過的 map 寫出去。這次不改「只併入、不刪掉消失持倉」。變更前 Redis 在逾時時會留下半套，變更後留下上一份完整內容。
- [已完成的 `rm-ibkr-risk-feed` 仍寫著舊鍵在每次回呼時寫] → 實作以這次規格為準，不回去滿足那句。那個 change 的 delta 不在主規格裡。日後若歸檔它，那條「舊餘額仍在回呼時寫入」要改成以 `account-balance-write` 為準，否則兩份主規格會衝突。
- [第一次連線的那一秒仍會為沒看過的合約各查一次] → 預期行為。110 檔只在記憶體還沒有時發生，不再每 30 秒發生。
- [刪檔當下已經寫進舊 inode 的那些行] → 回不來。下一行起才在新檔。這是刪檔本身的結果。
- [重開時再跑一次 10 天清理] → 接受。清理只刪更舊的日檔，不刪剛建的今天檔。

## Migration Plan

- 部署就是換上這個閘道程序並重啟。記憶體快取是空的，第一輪帳戶快照會把當下持倉的合約查一次並寫入 Redis。
- `IBGateway:{account}:balance` 的鍵名、TTL、JSON 形狀不變。下游不用改讀法。重啟後要等各戶第一次 `accountDownloadEnd`，鍵才會更新。
- 回滾是回到上一版程序。沒有要遷移或清掉的 Redis 資料。舊版會恢復成每筆回呼重寫 balance，也會恢復每 30 秒重查合約。
