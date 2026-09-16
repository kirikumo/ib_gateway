# IBKR 閘道 — 下單 SOP（外部）

下游系統不直連 TWS／IB Gateway。全部經 **Redis**：`PUBLISH` JSON 指令、訂閱推播、讀 key。

本文件只涵蓋委託：**下單、改價／改量、撤單、讀即時狀態、查成交**。

---

## 1. 你要先拿到的名字

向跑閘道的人要 `{gw}` 與真實 `{account}`；合約欄位自己組。

| 名稱 | 例子 | 用途 |
|---|---|---|
| `{gw}` | `paper1` | `TWS_GATEWAY_NAME`。CMD／ACK／status／AliveOid／CONNECTION |
| `{account}` | `DUQ453140` | IB 帳戶。下單、OMS、成交、資金 |
| `{exchange}` / `{pair}` | `SEHK` / `HKD-5` | OMS hash 的一段。不知道就聽 `O_channel`，不要猜 |

Redis 與閘道同一組。下文例子用 `paper1` / `DUQ453140` / `SEHK` / `HKD-5`，請換成你的值。

建議兩個 Redis 連線：

- 連線 A：先 `SUBSCRIBE` ACK 與 `O_channel`（先訂再發，否則會錯過推播）
- 連線 B：`PUBLISH` 指令，以及 `GET`／`HGET`／`HEXISTS`／`HGETALL`

---

## 2. 通訊模型

```
你
  PUBLISH  IBGateway:{gw}:CMD                 → 指令
  SUBSCRIBE IBGateway:{gw}:ACK                ← 心跳、ACK、order_error、TWS msg
  SUBSCRIBE URANUS:{account}:O_channel        ← 委託推播（訂單 JSON 字串）
  SUBSCRIBE URANUS:ID:{account}:O_channel     ← 委託推播（{oid: 訂單JSON字串}）
  HGET     URANUS:{exchange}:{account}:O:{pair}  ← 委託快照
  GET      其他結果 key                       ← 查詢類 CMD 的回傳
```

**先訂閱再發指令。**

每則 CMD 都是一個 JSON object，**必填**：

| 欄位 | 型別 | 說明 |
|---|---|---|
| `id` | number | 你自己遞增。ACK 的 `reqId` 原樣回來，用來對哪一筆指令 |
| `cmd` | string | 必須與下表完全一致（大小寫） |

```
PUBLISH IBGateway:paper1:CMD '{"id":1,"cmd":"PLACE_ORDER", ...}'
```

幾乎每則 CMD 都會在 ACK channel 回一則 `type: ack`（成功或失敗都回）。

### 2.1 ACK channel 上會出現什麼

`SUBSCRIBE IBGateway:paper1:ACK`

| `type` | 何時出現 | 含義 |
|---|---|---|
| `heartbeat` | 約每秒 | `{"type":"heartbeat","status":true/false,"t":<毫秒>}`。`false` 時不要下單 |
| `ack` | 每則 CMD 處理完 | 見下表 |
| `order_error` | 部分委託錯誤 | 見 §4.3、§5.1。用 `client_oid` 對單 |
| `msg` / `error` | TWS 訊息 | 合約找不到（code 200）等。下單路徑以 OMS 為準 |

指令 ACK：

```json
{"type":"ack","reqId":1,"ibApiId":10000010}
```

| 欄位 | 含義 |
|---|---|
| `type` | `ack` |
| `reqId` | 你的 `id` |
| `ibApiId` | IB 端請求編號。**`0` = 這次沒真正送到 IB**（CANCEL／EDIT 找不到單時**不會**帶 `err`） |
| `err` | 有這個欄位 = 指令當下失敗（字串原因） |
| `res` | 少數查詢才有（`ACCOUNT_LIST`）。下單／取消沒有 |

`PLACE` 的 ACK 只代表閘道把單交給 IB，**不是**已掛上。掛上、成交、取消終態都看 OMS。

### 2.2 閘道是否活著

```
GET IBGateway:paper1:status
```

內容與心跳同一份 JSON，要看到 `"status":true`。真正連上 IB 時另有一把短 TTL 旗標（約 2 秒），有值即可：

```
GET paper1_CONNECTION
```

（key = `{gw}_CONNECTION`。）沒有這個 key = 還沒連上 IB 或 Redis 斷了。

未完結 oid 列表（JSON 陣列；沒有活單則 `[]`）：

```
GET IBGateway:paper1:AliveOid
```

完結後該 oid 應從陣列消失。

---

## 3. 訂單身分與 OMS

### 3.1 oid = `orderRef`

你在 `PLACE_ORDER` 填的 `iborder.order.orderRef` 就是之後所有動作的 **oid**。

- **必須** `uranus_` 或 `api_` 開頭，否則 PLACE 當下失敗（ACK 帶 `err`：`Abort placing order without OMS client_oid`）
- 必須全域唯一，建議 `uranus_{bot}_{毫秒時間}`
- `CANCEL_ORDER` / `EDIT_ORDER` 的 `omsId` **必須與 `orderRef` 完全相同**
- 空的或不合前綴的 `orderRef`，閘道對不到單，CANCEL／EDIT 永遠 `ibApiId=0`

OMS 裡同一份 JSON 的 `i`、`client_oid`、`orderRef` 都應等於這個 oid。

### 3.2 委託寫在哪

| | Key / Channel | 內容 |
|---|---|---|
| Hash | `URANUS:{exchange}:{account}:O:{pair}` | field = oid，value = 訂單 JSON 字串；另有 field `t` = 此 hash 最後更新毫秒 |
| 推播 | `URANUS:{account}:O_channel` | 一張訂單的 JSON **字串**（不是再包一層） |
| 推播 | `URANUS:ID:{account}:O_channel` | `{ "<oid>": "<訂單JSON字串>" }`（value 再 `JSON.parse` 一次） |
| Flag | `URANUS:{exchange}:{account}:OMS` | 必須是 `1` 才算該交易所快取就緒 |

`{pair}`：

| 種類 | `{pair}` 例子 | 完整 hash key 例子 |
|---|---|---|
| 股票 | `HKD-5`、`USD-AAPL` | `URANUS:SEHK:{account}:O:HKD-5` |
| 期貨（乘數 1） | `USD-ES@202603` | `URANUS:CME:{account}:O:USD-ES@202603` |
| 期貨（有乘數） | `USD-BRR@20210625@5` | `URANUS:CMECRYPTO:{account}:O:USD-BRR@20210625@5` |
| 期權 | `{ccy}-{sym}@{expiry}@{mul}Call{strike}@{conid}` | 聽 `O_channel` 較穩 |

`exchange=SMART` 時，閘道用合約的 `primaryExch` 當 hash 的交易所那段（沒有 primary 才會寫成 `SMART`）。不知道交易所／pair 時：聽 `URANUS:{account}:O_channel`，用 `client_oid` 對。

例（港股 5）：

```
HEXISTS URANUS:SEHK:DUQ453140:O:HKD-5 uranus_bot1_1710000000000
HGET    URANUS:SEHK:DUQ453140:O:HKD-5 uranus_bot1_1710000000000
HGETALL URANUS:SEHK:DUQ453140:O:HKD-5
```

`HEXISTS` 回 `1` 才算這張單已進 OMS。進 OMS 之前不要 CANCEL／EDIT（ACK 會是 `ibApiId=0`，等於沒送到 IB）。

閘道斷線 teardown 會刪掉 `...:OMS`。沒有這個 key 或不是 `1`：**不要 PLACE／CANCEL／EDIT**。

### 3.3 訂單 JSON（OMS 與 `O_channel` 同一份）

```json
{
  "permId": "1234567890",
  "orderRef": "uranus_bot1_1710000000000",
  "client_oid": "uranus_bot1_1710000000000",
  "i": "uranus_bot1_1710000000000",
  "pair": "HKD-5",
  "T": "buy",
  "ttl_qty": 500,
  "p": 100.5,
  "avg_price": 100.5,
  "executed_qty": 0,
  "remained_qty": 500,
  "status": "Submitted",
  "t": 946656000000,
  "updateTime": 1710000005000,
  "market": "SEHK",
  "orderType": "LMT",
  "tif": "DAY",
  "whatIf": false,
  "secType": "STK",
  "commission": 0,
  "extMsg": null
}
```

| 欄位 | 用途 |
|---|---|
| `i` / `client_oid` / `orderRef` | 都應等於你的 oid |
| `status` | 狀態，見 §7 |
| `ttl_qty` | 原委託量（整數；碎股會被截斷） |
| `executed_qty` | 已成交量。`0` = 還沒成交 |
| `remained_qty` | 尚未成交的量 |
| `p` | 限價 |
| `avg_price` | **僅當 `executed_qty > 0` 才是成交均價**。未成交時閘道會把這個欄位填成 `p`，不能當成交 |
| `T` | `buy` / `sell`（小寫） |
| `permId` | 對逐筆成交用 |
| `market` / `pair` | 可拿來組 hash key |
| `updateTime` | Unix **毫秒**。**不要用 `t`**（固定佔位 2000-01-01） |
| `extMsg` | 取消／拒絕原因 |
| `commission` | 可能是 `0`（IB 還沒回手續費） |

看到這份 JSON，且 `status` 不是空的，才算「單已進入系統」，這時才能 CANCEL／EDIT。

### 3.4 `status`

閘道把下列視為**完結**（oid 會離開 `AliveOid`，之後 CANCEL／EDIT 會 `ibApiId=0`）：

| 狀態 | 含義 |
|---|---|
| `Filled` | 全部成交 |
| `Cancelled` | 已取消。請再看 `executed_qty` 是否 > 0 |
| `Rejected` | 沒掛上 |
| `ApiCancelled` | API 取消 |

還在場上（繼續等或再撤）：

| 狀態 | 含義 |
|---|---|
| `PendingSubmit` / `PreSubmitted` / `Submitted` | 送出中或已掛上 |
| `PendingCancel` | 取消處理中，繼續等 |
| `Inactive` | 對 `uranus_` 開頭的單，閘道**仍當成活單**（例如盤後在 TWS 掛的單）。`api_` 開頭則當完結 |
| `Unknown` / 其他 | 當還在場上 |

**部分成交後取消**：終態多半是 `Cancelled`，`executed_qty` 為已成交、`remained_qty` 為被取消的量。

建議超時（例如 10–30 秒）仍未完結就報錯，**不要假設已掛上或已取消**。

---

## 4. `PLACE_ORDER`

### 4.1 指令

```json
{
  "id": 1,
  "cmd": "PLACE_ORDER",
  "iborder": {
    "contract": {
      "symbol": "5",
      "secType": "STK",
      "exchange": "SEHK",
      "currency": "HKD"
    },
    "order": {
      "account": "DUQ453140",
      "T": "buy",
      "s": 500,
      "p": 100.5,
      "orderRef": "uranus_bot1_1710000000000",
      "tif": "DAY",
      "orderType": "LMT"
    }
  }
}
```

`iborder.contract` 欄位：

| 欄位 | 必填 | 說明 |
|---|---|---|
| `symbol` | 建議 | 代碼，港股匯豐為 `"5"` |
| `secType` | 建議 | `STK` 股票、`FUT` 期貨、`OPT` 期權、`FOP` 期權期貨、`CASH` 外匯 |
| `exchange` | 建議 | `SEHK`、`NYSE`、`NASDAQ`、`SMART`…。寫 `ISLAND` 會被閘道改成 `NASDAQ` |
| `currency` | 建議 | `HKD`、`USD`… |
| `conid` | 可選 | IB 合約數字 id，有的話最穩 |
| `primaryExch` | 可選 | SMART 時的真實交易所。OMS hash 會用這個而不是 `SMART` |
| `lastTradeDateOrContractMonth` | 期貨／期權 | 如 `202603`、`20260320` |
| `multiplier` | 期貨／期權 | 如 `"50"` |
| `strike` | 期權 | 履約價 |
| `right` | 期權 | `Call` 或 `Put` |
| `localSymbol` | 可選 | |
| `tradingClass` | 可選 | |

`iborder.order` 欄位：

| 欄位 | 必填 | 說明 |
|---|---|---|
| `account` | 是 | IB 帳戶，必須是 `ACCOUNT_LIST` 裡的值 |
| `T` | 是 | `buy` 或 `sell`（大小寫均可） |
| `s` | 是 | 數量 |
| `p` | 是 | 限價。市價單也要帶（閘道會原樣傳給 IB） |
| `orderRef` | **是** | oid，見 §3.1。必須 `uranus_` 或 `api_` 開頭 |
| `tif` | 否 | 缺省 `DAY`。常見 `DAY`、`GTC`、`IOC` |
| `orderType` | 否 | 缺省 `LMT`。常見 `LMT`、`MKT` |
| `outsideRth` | 否 | `true`／`false`，盤前盤後 |
| `whatIf` | 否 | `true` 只檢查保證金、不下真單 |
| `algo` | 否 | 如 `Adaptive` |
| `algoParams` | 否 | `[{"tag":"...","value":"..."}]` |
| `i` | 改單才用 | IB permId，一般下新單不要帶 |

### 4.2 怎樣算下單成功

1. ACK `reqId` 對得上，且**沒有** `err`
2. OMS 出現這張單：`O_channel` 推到該 oid，或 `HEXISTS ... {oid}` 為 `1`
3. `status` 不是空的。此時才能 CANCEL／EDIT

ACK 只代表「交給 IB」。不要把 `type: ack` 當成已掛上。

### 4.3 下單失敗

指令當下失敗（例如 `orderRef` 不合前綴）會在 ACK 帶 `err`，這張 oid **不會**進 OMS。

部分 IB 拒絕會寫進 OMS（`status=Rejected`，看 `extMsg`），**不一定**再推 `order_error`。

其餘委託錯誤可能在 ACK channel 出現：

```json
{
  "type": "order_error",
  "orderId": 15,
  "permId": 1234567890,
  "client_oid": "uranus_bot1_1710000000000",
  "code": 201,
  "msg": "Order rejected - reason:..."
}
```

用 `client_oid` 對單。`201`（拒絕）／`202`（取消）通常**只改 OMS** 的 `status`／`extMsg`，不一定再推 `order_error`。終態仍以 OMS 為準。

盤前 warning（`code: 399` 且訊息含 `will not be placed at the exchange until`）可忽略，單仍有效。

---

## 5. `CANCEL_ORDER`

```json
{
  "id": 2,
  "cmd": "CANCEL_ORDER",
  "omsId": "uranus_bot1_1710000000000"
}
```

| 欄位 | 必填 | 說明 |
|---|---|---|
| `omsId` | 是 | 與 PLACE 的 `orderRef` 完全相同 |

ACK 怎麼解讀：

- 有 `err`：指令失敗
- `ibApiId == 0`：**沒有送到 IB**。通常是 OMS 裡還沒這張單，或 IB 還沒配 orderId。閘道會順便刷新活單。處理：繼續等 §3 的訂單 JSON，然後 **再發一次同樣的 CANCEL_ORDER**（換新的 `id`）
- `ibApiId > 0`：取消請求已交給 IB。**還不是已取消。** 繼續看訂單 JSON 的 `status`

一次取消全部（小心，影響該閘道可見的所有活單）：

```json
{"id": 3, "cmd": "CANCEL_ALL"}
```

### 5.1 取消不了（code 161）

若 ACK channel 出現：

```json
{"type":"order_error","code":161,"client_oid":"uranus_bot1_1710000000000","msg":"...not in a cancellable state..."}
```

單**沒有**取消，隔 200–500ms 再 `CANCEL_ORDER`。

---

## 6. `EDIT_ORDER`

可改**限價**與／或**數量**。可只帶其中一個。

```json
{
  "id": 4,
  "cmd": "EDIT_ORDER",
  "omsId": "uranus_bot1_1710000000000",
  "changeInfo": {
    "p": 101.0,
    "s": 1000
  }
}
```

| 欄位 | 必填 | 說明 |
|---|---|---|
| `omsId` | 是 | = `orderRef` |
| `changeInfo.p` | 擇一 | 新限價 |
| `changeInfo.s` | 擇一 | 新數量 |

與 CANCEL 相同：必須先在 OMS；`ibApiId==0` 表示沒送到 IB（找不到單、`orderId` 還是 0、或狀態不是 active）。active 是指 `PreSubmitted`／`PendingSubmit`／`Submitted`／`PendingCancel`。

非 `uranus_`／`api_` 開頭的單無法改（內部會再走一次 PLACE 的 oid 檢查）。

改完後盯 OMS 的 `p`／`ttl_qty`。

---

## 7. 怎樣才算終態

盯同一把 hash field 或 `O_channel`，直到 `status` 進入 §3.4 的完結狀態。`AliveOid` 應不再包含該 oid。

| 你要確認的事 | 看什麼 |
|---|---|
| 全部成交 | `Filled`，`executed_qty == ttl_qty` |
| 完全沒成交就取消 | `Cancelled` 且 `executed_qty == 0` |
| 部分成交後取消 | `Cancelled` 且 `executed_qty > 0` |
| 被拒 | `Rejected`，原因在 `extMsg` |

---

## 8. 成交

### 8.1 即時：讀 OMS

同一份訂單 JSON：

- `executed_qty == 0`：沒成交
- `executed_qty > 0`：已成交數量；均價用 `avg_price`
- `Filled`：全部成交
- `Cancelled` + `executed_qty > 0`：部分成交後撤掉剩餘

數量是整數。

### 8.2 逐筆：`TradeReport`

```
HGETALL TradeReport:DUQ453140
```

每個 field 的 value 是一筆成交 JSON，例如：

```json
{
  "tradeKey": "....",
  "orderId": 15,
  "permId": 1234567890,
  "orderRef": "uranus_bot1_1710000000000",
  "acctNumber": "DUQ453140",
  "exchange": "SEHK",
  "side": "BOT",
  "shares": 500,
  "price": 100.4,
  "cumQty": 500,
  "avgPrice": 100.4,
  "time": "20260308  09:30:01",
  "execId": "0000xxxx",
  "commission": 5.2
}
```

**Hash 的 field 不是你的 oid。** 掃全部 value，留下：

- `orderRef` == 你的 `orderRef`，或
- `permId` == 訂單 JSON 裡的 `permId`

`commission` 可能比成交晚一點才寫上，可稍後再 `HGETALL` 一次。閘道連線後會自己收成交；若懷疑漏了：

```json
{"id": 5, "cmd": "REQ_EXECUTIONS"}
```

ACK 後再 `HGETALL TradeReport:{account}`。

### 8.3 資金／持倉（可選）

閘道自動寫（含現金與持倉）：

```
GET IBGateway:DUQ453140:balance
```

帳戶摘要（需先發 `FIND_ACCOUNT_SUMMARY`）：

```
GET IBGateway:Summary:DUQ453140
```

---

## 9. 開工輔助 CMD

### 9.1 帳戶列表 `ACCOUNT_LIST`

```json
{"id": 10, "cmd": "ACCOUNT_LIST"}
```

ACK 的 `res` 是 **JSON 字串**（再 parse 一次才是陣列），例如 `"[\"DUQ453140\"]"`。`PLACE_ORDER` 的 `account` 必須是這裡的值。

### 9.2 合約 `FIND_CONTRACTS`

不確定 IB 合約欄位時用。股票通常可直接 PLACE。

```json
{
  "id": 11,
  "cmd": "FIND_CONTRACTS",
  "contract": {
    "symbol": "5",
    "secType": "STK",
    "exchange": "SEHK",
    "currency": "HKD"
  }
}
```

結果**不在 ACK**，而在 Redis（TTL 約 32 天；第一次可能要等一兩秒）：

```
GET IBGateway:Contract:SEHK:STK:HKD-5
```

key = `IBGateway:Contract:{exchange}:{secType}:{pair}`。對外有用的欄位：

| 欄位 | 用途 |
|---|---|
| `contract.conid` | 之後 PLACE 可帶上，最穩 |
| `minTick` | 最小跳動（粗對齊；精確檔位看 MarketRule） |
| `minSize` / `sizeIncrement` / `suggestedSizeIncrement` | 最小數量、數量步進、建議步進。可為小數；`null` 表示 IB 未填或 invalid；`0` 是有效零。`suggestedSizeIncrement` 只供下單對齊，**不是**行情張數倍率 |
| `marketRuleIds` | 逗號分隔的規則 id |
| `validExchanges` | 可下單的交易所 |

查合約時閘道會順便寫價格檔：

```
GET IBGateway:MarketRule:2266
```

內容例：`[{"low_edge":0,"price_increment":0.001}, ...]`。找到 `price >= low_edge` 的最高檔，限價必須對齊 `price_increment`。

一次拿回所有匹配（TTL 300 秒）：

```json
{"id": 12, "cmd": "FIND_CONTRACTS_TO_REDIS", "contract": {"symbol":"5","secType":"STK","exchange":"SEHK","currency":"HKD"}}
```

```
GET IBGateway:ReqIdContract:12
```

內容是合約陣列的 JSON 字串。

### 9.3 刷新今日訂單 `REFRESH_TODAY_ORDERS`

OMS 對不上、CANCEL 一直 `ibApiId=0` 時可發。無額外 `res`；之後再讀 OMS。

```json
{"id": 13, "cmd": "REFRESH_TODAY_ORDERS"}
```

---

## 10. 建議流程

1. `SUBSCRIBE IBGateway:{gw}:ACK` 與 `URANUS:{account}:O_channel`（可再訂 `URANUS:ID:{account}:O_channel`）
2. `GET IBGateway:{gw}:status` 為 `status:true`，且 `GET {gw}_CONNECTION` 有值
3. `GET URANUS:{exchange}:{account}:OMS` 為 `1`
4. （可選）`ACCOUNT_LIST` 確認帳戶；`FIND_CONTRACTS` 對齊 `conid`／價格檔／數量步進
5. `PUBLISH ... PLACE_ORDER`，`orderRef` 用新的唯一 oid（`uranus_` 或 `api_` 開頭）
6. ACK `reqId` 對得上且無 `err`；若有 `err` → 結束（沒掛上）
7. 等到 OMS `HEXISTS` 為 1，或 `O_channel` 出現該 oid，且 `status` 非空 → 已進系統
8. 要撤：確認步驟 7 之後再 `CANCEL_ORDER`。`ibApiId==0` 就等 OMS 再發一次（換 `id`）。盯 `status` 到完結
9. 要改價／改量：同樣等步驟 7 之後 `EDIT_ORDER`，再看 OMS `p`／`ttl_qty`
10. 成交量以 OMS `executed_qty` 為準；需要逐筆再 `HGETALL TradeReport:{account}`

---

## 11. 從頭到尾：一組可複製的指令

以下 `{oid}` = `uranus_bot1_1710000000000`。

**連線 A（先執行）：**

```
SUBSCRIBE IBGateway:paper1:ACK
SUBSCRIBE URANUS:DUQ453140:O_channel
```

**連線 B：**

```
GET IBGateway:paper1:status
GET paper1_CONNECTION
GET URANUS:SEHK:DUQ453140:OMS
```

OMS 為 `1` 且 status 為 true 之後：

```
PUBLISH IBGateway:paper1:CMD '{"id":1,"cmd":"PLACE_ORDER","iborder":{"contract":{"symbol":"5","secType":"STK","exchange":"SEHK","currency":"HKD"},"order":{"account":"DUQ453140","T":"buy","s":500,"p":100.5,"orderRef":"uranus_bot1_1710000000000","tif":"DAY","orderType":"LMT"}}}'
```

連線 A 等到：

1. `{"type":"ack","reqId":1,...}` 且無 `err`
2. OMS 裡出現這張單，任一即可：
   - `O_channel` 出現 `client_oid` / `i` 等於 `{oid}` 的 JSON
   - `HEXISTS URANUS:SEHK:DUQ453140:O:HKD-5 uranus_bot1_1710000000000` 回 `1`，再 `HGET` 同一把 key

然後：

```
PUBLISH IBGateway:paper1:CMD '{"id":2,"cmd":"CANCEL_ORDER","omsId":"uranus_bot1_1710000000000"}'
```

- ack `ibApiId==0` → 等 OMS 裡這張單還在，再 PUBLISH 一次 CANCEL（`id` 改 3）
- ack `ibApiId>0` → 繼續聽 OMS，直到該 oid 的 `status` 為 `Cancelled` 或 `Filled` 或 `Rejected`

最後再讀一次 OMS：

```
HGET URANUS:SEHK:DUQ453140:O:HKD-5 uranus_bot1_1710000000000
```

讀 `executed_qty`。需要逐筆再：

```
HGETALL TradeReport:DUQ453140
```

過濾 `orderRef` == `{oid}`。

對一下帳戶現金／持倉（可選）：

```
GET IBGateway:DUQ453140:balance
```

---

## 12. 不要做的事

- 只聽 ACK、不讀 OMS，把 ACK 當「已掛單／已取消」
- PLACE 的 ack 一到就 CANCEL（這時常常 `ibApiId=0`，等於沒取消）
- `orderRef`／`omsId` 不用 `uranus_`／`api_` 開頭
- 重複使用 `orderRef`
- 用訂單 JSON 的 `t` 當時間
- `executed_qty == 0` 卻把 `avg_price` 當成交價
- 看到 161 卻當成已取消
- OMS flag 還不是 `1` 就 PLACE／CANCEL／EDIT
- 對完結狀態再 CANCEL／EDIT（會 `ibApiId=0`）
- 假設 hash field 是逐筆成交的 oid（`TradeReport` 的 field 是 IB `tradeKey`）
- 對 SMART 單用 `URANUS:SMART:...` 去 HEXISTS（實際多半寫在 `primaryExch`）

---

## 13. CMD 速查

| `cmd` | 你要帶的欄位 | 結果在哪 |
|---|---|---|
| `PLACE_ORDER` | `iborder.contract`、`iborder.order`（含 `orderRef`） | ACK（無 `err`）+ 之後 OMS／`O_channel` |
| `CANCEL_ORDER` | `omsId`（= `orderRef`） | ACK；終態在 OMS |
| `EDIT_ORDER` | `omsId`、`changeInfo`（`p` 改價、`s` 改量） | ACK；OMS `p`／`ttl_qty` |
| `CANCEL_ALL` | 無 | ACK；各單終態在 OMS |
| `ACCOUNT_LIST` | 無 | ACK `res`（JSON 字串陣列） |
| `FIND_CONTRACTS` | `contract` | `IBGateway:Contract:{exchange}:{secType}:{pair}` |
| `FIND_CONTRACTS_TO_REDIS` | `contract` | `IBGateway:ReqIdContract:{id}`（TTL 300 秒） |
| `REQ_EXECUTIONS` | 無 | `TradeReport:{account}` |
| `REFRESH_TODAY_ORDERS` | 無 | 之後再讀 OMS |
| `FIND_ACCOUNT_SUMMARY` | 無 | `IBGateway:Summary:{account}` |

主路徑是 `PLACE_ORDER` 與 `CANCEL_ORDER`；改價／改量用 `EDIT_ORDER`；成交對帳用 OMS 即時欄位，必要時再讀 `TradeReport`。
