# scale-trader：下流要接的變更

這一版可以下 ScaleTrader，券商派生的子單用另一把鍵。普通單的鍵、狀態與價格規則不變。閘道不算網格，只把參數交給 IB，再把 IB 回來的單照下面的鍵寫出。

自己的 `orderRef` 不要含 `@`。子單鍵是母單 `orderRef` 後面加 `@` 與子單 `permId`。

## 下單

`PLACE_ORDER` 的 `iborder.order` 可以帶這些欄位，名字與 IB 相同。沒帶的維持 IB 預設。`orderType` 仍是 `LMT` 這類原有類型。

`EDIT_ORDER` 的 `changeInfo` 可以帶同樣的欄位，也仍可以用 `p`、`s`。只改有帶的鍵。改 Scale 時 `omsId` 用母單的 `orderRef`。

| 欄位 | 含義 |
|---|---|
| `scaleInitLevelSize` | 第一檔數量 |
| `scaleSubsLevelSize` | 之後每檔數量 |
| `scalePriceIncrement` | 每檔價差 |
| `scalePriceAdjustValue` | 價格調整量 |
| `scalePriceAdjustInterval` | 價格調整間隔，秒 |
| `scaleProfitOffset` | 止盈價差 |
| `scaleAutoReset` | 成交後是否重設網格 |
| `scaleInitPosition` | 重設時的起始部位 |
| `scaleInitFillQty` | 起始已成交量 |
| `scaleRandomPercent` | 數量是否隨機 |
| `scaleTable` | 自訂階梯字串 |

## OMS

母單的 `orderRef`、`client_oid`、`i` 就是下單時那把字串。

有設值的 Scale 欄位出現在同一份訂單 JSON。沒設的不出現。`scaleAutoReset` 與 `scaleRandomPercent` 只有 `true` 才出現。

券商派生、而且 `orderRef` 抄了母單、`permId` 已大於 0 的子單，是另一筆：

- `orderRef`、`client_oid`、`i` 為 `母單orderRef@子單permId`。已經是這個結尾就不再加。
- 另有 `parentId`（數字，非 0 才有）與 `parentPermId`（字串，非 0 才有）。OMS 的 `permId` 本來就是字串。
- 沒有 `parentOrderRef`。母單識別用 `@` 左邊，或用 `parentPermId`。
- 母單那一筆保持原樣。多張子單各佔一個 hash field。

`permId` 仍是 0 的子單不寫出，也不佔母單那格。子單自己沒有 `orderRef` 時，鍵仍是該子單的 `permId` 字串，不加後綴。

取消或修改某一張子單時，`omsId` 用它的對外鍵。`IBGateway:{gateway}:AliveOid` 會含這些鍵。那是券商掛出的單。

## 風控 `RM:IBKR`

`RM:IBKR:{account}:open` 的 field，以及 `RM:IBKR:{account}:order` 的 `orderRef`，對這種子單同樣是 `母單orderRef@子單permId`。母單 field 不變。

風控 JSON 的欄位與以前相同。Scale 參數、`parentId`、`parentPermId` 都在 OMS，不在這份。認子單只看 `orderRef`。

母單的 `qty` 是整張 Scale 的總量。每張子單有自己的 `side`、`price`、`qty`，常見是反向止盈。母單剩餘是還沒建完的量，子單是另一筆委託。兩筆分開算，不要加成同一筆新開倉。

`permId` 仍是 0 的子單不寫、不推。

重連後，新快照裡已經沒有、但還留在 `open` 的單，要等這次當日成交回報結束，才推 `removed`，或對上的 `filled`、`cancelled`、`rejected`。兩個訂單快照先結束時，那些 field 還在。

## 成交

成交已經對得上訂單時，成交 JSON 的 `orderRef` 用對外鍵。

子單的成交比該子單的委託先到時，這一筆先用券商抄來的母單字串。之後同一筆成交重播，且子單已在訂單快照裡，會改寫成子單鍵。先到的那一筆不要記成母單自己的成交。風控的 `open` 與 `order` 不會因為這一筆先到的成交被改寫。

## 重啟

已經寫成 `母單orderRef@permId` 的 OMS field 與 `open` field 會對上，不用搬資料。先前誤寫在母單鍵上的內容，會在母單下一筆狀態裡被母單自己的內容蓋掉。
