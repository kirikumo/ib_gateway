# scaletrader-order Specification

## Purpose

支援透過 Redis `PLACE_ORDER` 指令向 IBKR 下達 ScaleTrader 演算法訂單，並在訂單狀態回報（`RM:IBKR:{account}:order`、`open` 及 OMS 頻道）中完整反映 ScaleTrader 參數與狀態，使外部網格或演算法交易策略能夠精確控制分批建倉與自動停利。

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
系統 SHALL 在產生 OMS 訂單狀態 JSON（`toOMSJSON()`）時，將生效中的 ScaleTrader 欄位序列化輸出；未設定的預設值（`Integer.MAX_VALUE`、`Double.MAX_VALUE`、`false` 或 `null`）MUST NOT 出現在輸出 JSON 中。

#### Scenario: 活躍 Scale 訂單回報
- **WHEN** 委託具有已生效的 `scaleInitLevelSize` 或 `scaleProfitOffset` 等非預設值
- **THEN** 產生的 JSON 鍵值對 MUST 包含對應的欄位與數值

#### Scenario: 常規訂單回報乾淨無污染
- **WHEN** 委託為未設定 ScaleTrader 參數的常規訂單
- **THEN** 產生的 JSON MUST NOT 包含任何 `scale*` 欄位

### Requirement: 網關與策略完全解耦
系統 SHALL 將 ScaleTrader 參數直接映射至 IBKR 官方 API 欄位，網關層 MUST NOT 內建策略計算或網格狀態機。
