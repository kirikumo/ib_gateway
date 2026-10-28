## Why

2026-10-28 一小時的閘道日誌有 13.3MB、約 13 萬行。磁碟還撐得住（一天約 320MB，日檔留 10 天約 3GB），但內容幾乎是同一輪帳戶快照在複誦：`DU166679` 的 110 檔持倉每 30 秒整秒重跑一次，已在記憶體裡的合約仍向 IB 送出約 110 次 `reqContractDetails`，舊的 `IBGateway:{account}:balance` 也在每一筆持倉回呼把三個帳戶整包重寫。日誌變大是這個熱路徑的症狀。

## What Changes

- 記憶體裡已有細節的合約，不因為 30 秒帳戶快照又出現而再向 IB 送 `reqContractDetails`，也不重寫該合約的 Redis 快取。新出現的持倉仍立刻查。若要刷新合約內容或 Redis TTL，間隔必須遠長於 30 秒（小時到一天；具體數字留在 design）。
- `IBGateway:{account}:balance` 改為該戶這輪 `accountDownloadEnd` 才寫一次完整內容。快照進行中的現金與持倉回呼只更新記憶體，不寫這個鍵。這輪沒有 End（逾時或斷線）時，鍵維持上一份完整內容。鍵名、JSON 格式、TTL 不變。
- `RM:IBKR:{account}:balance` 與 `RM:IBKR:{account}:summary` 的寫入條件維持現狀：只在該戶 End、這輪沒有 `AccountReady=false`、且有無後綴 `NetLiquidation` 時寫。`AccountReady=false` 的 End 仍可寫舊的 `IBGateway:{account}:balance` 一次，不把 RM 那組條件套到舊鍵上。
- 逐筆的合約查詢、持倉與 `>>> Redis ...:balance` 日誌隨著上述工作消失。每一戶的 `accountDownloadEnd` 仍留下可辨認的一行。不另做 log level，也不改日檔保留天數。
- 程序還在跑時，當天的 `logs/<gateway>/ib_gateway.yyyy-MM-dd.log` 被刪掉，或路徑上換成另一個檔，下一行終端機輸出必須把這一行寫進原路徑上的檔。刪掉的舊內容不回來。沒有新輸出時不為了補檔建空檔。

## Capabilities

### New Capabilities

- `contract-details-query`: 帳戶快照重複帶出已知道的合約時，不再向 IB 重查、不再重寫該合約的 Redis 快取；新合約仍立刻查。
- `account-balance-write`: `IBGateway:{account}:balance` 只在該戶這輪 `accountDownloadEnd` 寫一次完整內容；回呼途中與沒有 End 的一輪都不寫。

### Modified Capabilities

- `daily-log`: 當天日檔的路徑被拿掉，或換成另一個檔時，下一行輸出寫回原路徑。沒有新輸出不建空檔。保留天數與換日規則不變。
- 主規格 `contract-details-cache` 只規範數量欄位編碼，這次不改那些 requirement。`openspec/specs/` 沒有帳戶 balance 的既有 requirement。

## Impact

- `ContractDetailsHandler.findDetails` / `queryDetails`：現在先送查詢，10 秒冷卻又短於 30 秒週期，所以每一輪 110 檔全部重查。`queryContractListWithCache`、下單路徑也呼叫 `findDetails`，快取命中時行為要維持「拿得到細節、不重複打 IB」。
- `AccountMVHandler.writePosition`：初始化之後每次現金或持倉回呼都會寫舊鍵。`AccountMVHandlerRmTest` 目前斷言初始化之後的增量 `updatePortfolio` 會再寫一次 `IBGateway:{account}:balance`，這條要改成等到該戶 End。`RmAccountFeed` 的寫入條件不動。
- 已完成的 change `rm-ibkr-risk-feed` 寫過「舊鍵仍在每次回呼時寫」。那句被這次取代；它不在主規格裡。
- 不改 30 秒帳戶輪詢、不改 `com.ib/`、不新增 logging 函式庫。Redis 合約鍵 TTL 仍是寫入時設定的 32 天，所以長間隔刷新要負責把 TTL 延上。
- `DailyLog`：當天 writer 一直開著。路徑被 `rm` 之後，後續輸出仍寫進已刪除的 inode，目錄裡不會再出現這個檔，直到換日或重啟。這次讓下一行發現路徑不見或已是另一個檔時，關掉舊 writer 再用原路徑建立。
