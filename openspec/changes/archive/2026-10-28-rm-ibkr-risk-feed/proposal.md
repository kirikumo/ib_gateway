## Why

風控要讀一份與 OMS 分開的 Redis 契約（`doc/ibkr_risk_feed.md`）：委託與連線即時，餘額與摘要只在完整帳戶影像收完後寫入。現在閘道只寫 `IBGateway:*` 與 `URANUS:*`，而且帳戶輪詢固定睡 3 秒就換戶，常常在 `accountDownloadEnd` 之前就把訂閱取消，新鍵無法照契約更新。

## What Changes

- 新增 `RM:IBKR:{gateway}:connection`、`RM:IBKR:{account}:open`、`RM:IBKR:{account}:order`、`RM:IBKR:{account}:balance`、`RM:IBKR:{account}:summary`。既有鍵的格式與寫入路徑維持不變。
- 委託用獨立 JSON：小數數量、可為 `null` 的價格、`TRAIL` / `TRAIL LIMIT` 不填可乘價格、`PreCancelled` 留在未完成單。不走 `toOMSJSON()`、`isAlive()`、`IBContract.pair()`。
- 重連對帳等未完成單快照與當日完結單都到齊，再處理仍留在 `open`、但新快照沒有的 `orderRef`。不補從未進過該 hash 的舊單。
- 餘額與摘要只在該戶這輪 `accountDownloadEnd` 且 `AccountReady` 不是 false 時一起寫。`AccountReady=false` 或影像沒收完則兩個新鍵都不動。
- 帳戶市值輪詢改為停掉上一戶、訂下一戶、等到該戶 `accountDownloadEnd` 或逾時再換。舊的 `IBGateway:{account}:balance` 仍在每次回呼時照原格式寫。30 秒到了若上一輪還在跑則略過。秒級心跳不負責等待影像。

## Capabilities

### New Capabilities

- `rm-ibkr-risk-feed`: 依 `doc/ibkr_risk_feed.md` 寫入風控 Redis 鍵，含連線心跳、委託推送、重連對帳，以及慢速餘額與摘要。

### Modified Capabilities

- 無。主規格裡沒有帳戶市值輪詢的 requirement。等待 `accountDownloadEnd` 不改既有鍵的格式，也不改 `IBGateway:Summary:{account}` 的訂閱方式。

## Impact

- `GatewayController` 的連線心跳與帳戶市值輪詢。
- `AllOrderHandler` 旁路：未完成單、完結單、成交與佣金。OMS 初始化仍在 `openOrderEnd`，不與風控對帳綁在一起。
- `AccountMVHandler` 旁路：只為新鍵組快照。`reqAccountSummary` 維持現狀。
- `ApiController.reqCompletedOrders` 已使用 `apiOnly=false`，不改 `com.ib`。
- 下游新讀者訂 `RM:IBKR:*`。舊的 OMS 與 `IBGateway:*` 讀者不受格式變更。
