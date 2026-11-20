# scaletrader-order Specification

## Purpose

支援透過 Redis `PLACE_ORDER` 指令向 IBKR 下達 ScaleTrader 演算法訂單，並支援透過 `EDIT_ORDER` 動態修改運行中 ScaleTrader 訂單的參數（包含價格、最大持倉、止盈偏移 PT 等），在訂單狀態回報（`RM:IBKR:{account}:order`、`open` 及 OMS 頻道）中完整反映 ScaleTrader 參數與狀態，使外部網格或演算法交易策略能夠精確控制分批建倉、動態鎖倉與多個止盈子單獨立管理。

## Requirements

### Requirement: ScaleTrader 訂單參數解析
系統 SHALL 在解析 `PLACE_ORDER` 委託 JSON 時，正確提取並設定 11 個 ScaleTrader 原生參數至底層 `com.ib.client.Order` 物件。

#### Scenario: 網格核心參數設定
- **WHEN** 委託 JSON 包含 `scaleInitLevelSize`、`scaleSubsLevelSize`、`scalePriceIncrement` 與 `scaleProfitOffset`
- **THEN** 底層委託物件 MUST 正確設定上述欄位數值，且該委託標記為待送出 (`toBePlaced = true`)

#### Scenario: 網格自動重設與進階參數設定
- **WHEN** 委託 JSON 包含 `scaleAutoReset`、`scaleInitPosition`、`scaleInitFillQty`、`scaleRandomPercent` 或 `scaleTable`
- **THEN** 底層委託物件 MUST 正確設定相應的布林值、整數或自訂階梯字串

#### Scenario: 缺省參數安全處理
- **WHEN** 委託 JSON 未包含任一 ScaleTrader 欄位
- **THEN** 系統 MUST 保持該欄位的原生預設值（例如數值型為 `MAX_VALUE`），且 MUST NOT 影響常規委託（如普通 LMT 或 STP 單）的正常下單

### Requirement: ScaleTrader 訂單狀態序列化 (toOMSJSON)
系統 SHALL 在產生 OMS 訂單狀態 JSON（`toOMSJSON()`）時，將生效中的 ScaleTrader 欄位序列化輸出；未設定的預设值（`Integer.MAX_VALUE`、`Double.MAX_VALUE`、`false` 或 `null`）MUST NOT 出現在輸出 JSON 中。

#### Scenario: 活躍 Scale 訂單回報
- **WHEN** 委託具有已生效的 `scaleInitLevelSize` 或 `scaleProfitOffset` 等非預設值
- **THEN** 產生的 JSON 鍵值對 MUST 包含對應的欄位與數值

#### Scenario: 常規訂單回報乾淨無污染
- **WHEN** 委託為未設定 ScaleTrader 參數的常規訂單
- **THEN** 產生的 JSON MUST NOT 包含任何 `scale*` 欄位

### Requirement: 衍生止盈子單唯一鍵位派生與母子層級關聯
系統 SHALL 在接收到由 ScaleTrader 或其他演算法派生之子單（`parentId != 0 || parentPermId != 0`）時，依據母單原始標識符與子單的全局唯一 `permId` 派生出獨立標識符 `${parentOrderRef}@${childPermId}`，並在 OMS JSON 中暴露父子層級結構。母單與各子單 MUST 在 Redis 哈希表與記憶體快取中獨立共存，不得互相覆蓋。

#### Scenario: 止盈子單唯一鍵位派生
- **WHEN** 系統收到關聯父單的衍生子單且其 `permId` 大於 0
- **THEN** 該子單對外輸出的 `orderRef`、`client_oid` 與 `i` MUST 為 `${parentOrderRef}@${childPermId}`，且不得重複追加後綴

#### Scenario: 多個止盈子單並存不衝突
- **WHEN** 同一個母單先後或同時派生多個具備不同 `permId` 的止盈子單
- **THEN** 各子單在 Redis 哈希表中 MUST 以各自的 `${parentOrderRef}@${childPermId}` 分別儲存，且母單記錄 MUST 保持完整

#### Scenario: 父子層級關聯欄位輸出
- **WHEN** 系統序列化衍生子單的 OMS JSON
- **THEN** JSON 內 MUST 包含 `parentId` 與非零的 `parentPermId`；策略端可由 `orderRef`（`${parentOrderRef}@${childPermId}`）直接解析母單標識符，故 JSON 內不重複輸出 `parentOrderRef`；常規非衍生訂單 MUST NOT 包含上述欄位

### Requirement: ScaleTrader 訂單動態修改 (EDIT_ORDER / placeOrder)
系統 SHALL 支援透過 `EDIT_ORDER` 指令動態修改進行中（處於活動狀態）的 ScaleTrader 訂單參數。修改時系統 MUST 保留訂單既有的內部 `orderId` 與全局 `permId`，就地向 IBKR 底層送出修改請求，不得建立重複委託。

#### Scenario: 動態調整最大持倉與軟鎖倉
- **WHEN** 外部透過 `EDIT_ORDER` 傳入新的數量 `s`（`totalQuantity`），包含調整為更大持倉量或下調至當前已成交量實現軟鎖倉
- **THEN** 系統 MUST 將底層 `Order` 物件的 `totalQuantity` 更新為新數值，以原 `orderId` 呼叫 IBKR 修改介面，且修改成功後 OMS JSON 中的 `ttl_qty` 與 `remained_qty` 正確更新

#### Scenario: 動態調整止盈偏移量 (PT)
- **WHEN** 外部透過 `EDIT_ORDER` 傳入新的 `scaleProfitOffset` 數值
- **THEN** 系統 MUST 將底層 `Order` 物件的 `scaleProfitOffset` 更新為新數值，並送出修改，使後續生效之止盈單反映新的利潤空間，且 OMS JSON 正確反映更新後的 `scaleProfitOffset`

#### Scenario: 就地修改維持標識唯一性
- **WHEN** 系統執行 ScaleTrader 訂單修改
- **THEN** 該訂單的 `orderRef`、`client_oid`、`orderId` 與 `permId` MUST 保持不變，不得產生新訂單或覆蓋其他鍵位

### Requirement: 網關與策略完全解耦
系統 SHALL 將 ScaleTrader 參數直接映射至 IBKR 官方 API 欄位，網关層 MUST NOT 內建策略計算或網格狀態機。
