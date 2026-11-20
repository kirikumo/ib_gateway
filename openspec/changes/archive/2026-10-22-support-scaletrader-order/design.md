## Context

Interactive Brokers (IBKR) 的 ScaleTrader 是一種在伺服器端執行的演算法訂單，專為大單分批進場與網格交易設計。當用戶設定好初始檔位、後續檔位大小、價格間距與利潤偏移後，IBKR 撮合中心會在底層初始部位成交時，自動掛出相反方向的停利單（Profit-Taking Order），並在利潤單成交後根據 AutoReset 設定自動重新掛回網格單。

`ib_gateway` 作為與 IBKR API 通訊的橋樑，內部透過 `IBOrder` 類別將 Redis 收到的委託 JSON 轉換為 TWS API 的 `com.ib.client.Order` 物件，並將委託狀態轉換為 OMS JSON 發布。此前 `IBOrder` 未解析 ScaleTrader 欄位，導致該功能無法使用。

## Goals / Non-Goals

**Goals:**

- 完整支援 TWS 中用戶能手動設定的所有 11 個 ScaleTrader 原生參數。
- 雙向支援：下單端解析（JSON -> `Order`）與狀態回報序列化（`Order` -> OMS JSON）。
- 網關保持無狀態、高純淨度，不碰策略邏輯，不修改 `com.ib` SDK 代碼。
- 經由實盤沙盒（帳號 `DUQ453614`，CME MES 期貨合約）進行端到端成交與停利單生成驗證。

**Non-Goals:**

- 網關內部不維護網格狀態機，不參與調倉或網格動態試算，完全交由 IBKR 機房演算法引擎原生處理。
- 不修改 `com.ib.client.Order` 原始類別。
- 不引入額外的外部相依套件。

## Decisions

### 1. 完整覆蓋 11 項 ScaleTrader 參數
- **選擇**：支援全部 11 個欄位（`scaleInitLevelSize`, `scaleSubsLevelSize`, `scalePriceIncrement`, `scalePriceAdjustValue`, `scalePriceAdjustInterval`, `scaleProfitOffset`, `scaleAutoReset`, `scaleInitPosition`, `scaleInitFillQty`, `scaleRandomPercent`, `scaleTable`）。
- **理由**：用戶可能使用固定間距網格、動態時間調整間距網格，或是透過 `scaleTable` 自訂階梯網格。一次性完整還原 TWS 原生能力，避免後續策略擴充時重複修改網關。
- **替代方案**：只支援最常見的 4 個參數（否決，會限制未來靈活性）。

### 2. 序列化過濾 `MAX_VALUE` 預設值
- **決策**：在 `toOMSJSON()` 中，數值型欄位僅在不等於 `Integer.MAX_VALUE` / `Double.MAX_VALUE` 時輸出；布林值僅在為 `true` 時輸出；字串僅在非空時輸出。
- **理由**：TWS API 對未設定的數值欄位預設賦予 `MAX_VALUE`。若直接輸出會造成巨大冗餘，且會污染普通限價單的 JSON 結構。

### 3. 沙盒帳號嚴格隔離測試
- **決策**：實盤下單與成交驗證嚴格限制在 `DUQ453614` 模擬帳戶上執行。

## Risks / Trade-offs

- **Tick Size 限制**：IBKR 要求 `scalePriceIncrement` 與 `scaleProfitOffset` 必須是標的合約最小跳動單位（Min Tick）的整數倍。若不符合規則，TWS 會拒絕訂單。此驗證由 IBKR 服務端原生執行，網關轉發錯誤訊息，不在網關做合約元數據的硬編碼校驗。
