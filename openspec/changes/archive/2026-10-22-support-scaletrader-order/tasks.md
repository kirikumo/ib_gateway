## 1. 訂單模型擴充 (IBOrder.java)

- [x] 1.1 在 `IBOrder` 建構子中解析 11 個 ScaleTrader 欄位並賦值至 `order` 物件。驗證：傳入包含 ScaleTrader 參數的 JSON 時，底層 `order` 物件正確儲存對應參數。
- [x] 1.2 在 `toOMSJSON()` 中增加 ScaleTrader 欄位序列化邏輯，並過濾未設定的 `MAX_VALUE` 預設值。驗證：序列化後產生的 JSON 正確攜帶已設定之 Scale 欄位，未設定欄位不輸出。

## 2. 專案構建與代碼整潔

- [x] 2.1 透過 Maven 打包專案。驗證：執行 `mvn clean package -DskipTests` 編譯成功，生成 `target/ibgateway-3.1.jar`。
- [x] 2.2 更新 `.gitignore` 排除 IDE 設定與暫存檔案。驗證：`git status` 不出現無關 `.idea/` 或 `.iml` 變更。
- [x] 2.3 保持最小化變更，還原無關檔案修改。驗證：`git diff` 僅包含 `IBOrder.java` 與 `.gitignore`。

## 3. 端到端實盤沙盒測試

- [x] 3.1 透過 Redis 向帳號 `DUQ453614` 下達 CME MES 期貨合約 ScaleTrader 買單。驗證：IBKR 接受委託並成功撮合首筆部位。
- [x] 3.2 驗證自動停利賣單（Profit-Taking Order）產生。驗證：首筆買單成交後，IBKR 自動在 `lmtPrice + scaleProfitOffset` 掛出對應的獲利賣單。

## 4. 整體驗收

- [x] 4.1 執行全量單元測試。驗證：`mvn test` 執行 45 項測試全部通過，BUILD SUCCESS。
