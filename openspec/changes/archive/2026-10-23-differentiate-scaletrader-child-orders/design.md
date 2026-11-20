## Context

IBKR ScaleTrader 是一種原生服務端演算法。當母單的 Working Component 開倉成交後，伺服器會自動掛出反向的 Profit Taking（PT）限價止盈單。在 TWS 協議中，衍生子單具備獨立的 `orderId` 與全局唯一的 `permId`，但其 `orderRef` 會繼承母單設定的值。

網關內部使用 `orderRef`（`client_oid`）作為訂單在 Redis 哈希與記憶體快取中的主鍵。現有實現未考慮衍生子單的鍵位衝突，造成後到的止盈子單將母單覆蓋。

## Goals / Non-Goals

**Goals:**

- 為每一個衍生子單產生確定性、唯一的檢索鍵位：`${parentOrderRef}@${childPermId}`。
- 支援同一個母單下存在多個並行或先後生成的止盈子單，確保互不覆蓋。
- 在 `toOMSJSON()` 中顯式暴露 `parentId`、`parentPermId` 與 `parentOrderRef`，建立清晰的結構化層級關係。
- 支援精確撤單：外部策略可透過傳入子單專屬鍵位單獨撤銷該子單。
- 保持底層與風控系統的連鎖一致性（`RM:IBKR:*` 與 `TradeReport:*` 同步使用該鍵位）。

**Non-Goals:**

- 不在網關內預測或自行合成子單的價格與數量，所有子單狀態與數值均以 IBKR 伺服器實際推播為準。
- 不修改 `com.ib` SDK 官方類別。
- 不改變沒有父單關聯的普通訂單（`parentId == 0 && parentPermId == 0`）之任何行為。

## Decisions

### 1. 鍵位格式選型：`${parentOrderRef}@${childPermId}`
- **決策**：採用 `${parentOrderRef}@${childPermId}`，例如 `api_scale_1792635490740@812216931`。
- **評估**：
  - 若採用靜態後綴 `@child`：當同一個母單因分批成交或網格波動生成多個 PT 單時，各子單間仍會互相覆蓋。
  - 若採用 `orderId`（本地整數自增 ID）：網關重啟或重連時 `orderId` 可能重排或失真。
  - 採用 `childPermId`：IBKR 全局永久唯一整數，保證在重連、跨交易日及長跑 JVM 中 100% 不衝突，且維持以 `@` 分隔的既有層級風格。

### 2. 規範化時機：底層物件接收期
- **決策**：在 `IBOrder` 接收並設定狀態時，若判定 `parentId != 0 || parentPermId != 0`，即將內部的 `orderRef` 與 `client_oid` 規範化，同時保留 `parentOrderRef`。
- **理由**：這樣無論是 OMS 寫入、記憶體索引（`_orderByOMSId`）、撤單匹配（`cancelOrder`）、風控（`RmOrderBuilder`）還是存活追蹤（`_aliveOids`），都能以極簡的代碼量實現全域統一，避免在各個外圍系統重複拼接。

### 3. OMS JSON 擴充結構
- **決策**：針對子單，在 OMS JSON 中輸出：
  - `orderRef`: `api_scale_1792635490740@812216931`
  - `client_oid`: `api_scale_1792635490740@812216931`
  - `i`: `api_scale_1792635490740@812216931`
  - `parentId`: `1`
  - `parentPermId`: `"812216930"`
  - `parentOrderRef`: `"api_scale_1792635490740"`
- **理由**：既便於下游策略透過鍵位檢索，又具備完備的結構化父子節點關聯。

## Risks / Trade-offs

- **重復規範化風險**：`openOrder` 與 `orderStatus` 會多次更新同一筆訂單。
  - **緩解**：檢查 `orderRef` 若已包含 `@`，則不再重復追加後綴，保證冪等性。
- **重連時 permId 為 0 的邊界狀態**：
  - **緩解**：子單必有 `parentId != 0` 或 `parentPermId != 0`，若初始 `permId` 為 0 則等待 `orderStatus` 傳入正確 `permId` 時更新主鍵並寫入快取。
