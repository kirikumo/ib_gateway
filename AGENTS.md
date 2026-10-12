# AGENTS.md

對使用者的說明用繁體中文。程式註解用英文。

## Coding Conventions & Style Guide

### 註解

- 只用英文。
- 只在邏輯複雜或意圖不明時加。不必解釋 `i++` 這類一眼能看懂的語句。
- 寫原因或約束，保持短。不要逐步複述程式在做什麼。
- 只給測試用的程式要註明。包含 mock、test double，以及正式路徑不會呼叫的注入點（假時鐘、測試用 writer、縮短的逾時）。註解寫 `Test only` 或 `Test double`，並點出正式路徑用哪一個。

### 修改範圍

- 不要改 `src/main/java/com/ib/`。那是 Interactive Brokers 的官方程式。
- 跟著正在改的那個檔案的縮排與括號風格。不要順手重排無關程式。
- 沒有被要求時不要加測試。
- 不要假設帳戶基礎幣別是 USD，也不要寫死某個幣別。

### 語言與建置

- `pom.xml` 的 source / target 是 14。新程式維持 Java 14，不要用 record（Java 16）。
- 編譯與 `mvn test` 用 Java 17 以上。機器上的預設 `java` 可能是 8，那個跑不起來。
- 既有測試是 JUnit 4（`org.junit.Test`）。
- 測試不要載入 `com.bitex.util.Redis`。那個類別一被用到就會依環境變數建立連線池。
