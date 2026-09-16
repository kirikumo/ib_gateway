## Context

見 `proposal.md` 的 Why。現況約束：

- IB inbound 只有一條 `ApiController.startMsgProcessingThread` 執行緒呼叫 `EReader.processMsgs()`；該迴圈只捕捉 `IOException`。
- `EReader.processMsgs()` 會一次抽出佇列內多筆；`getMsg()` 先出隊再解碼。回調中途丟例外時，當筆已離開佇列，其餘仍留在佇列。
- `TopMktDataHandler` / `OptionTopMktDataHandler` 對 `TickType` 使用 enum `switch`，javac 生成 `$SwitchMap` 合成內部類，第一次走到 `tickPrice` 才載入。`marketDataType` / `tickReqParams` 不載入該類，所以 log 能印完再炸。
- `IConnectionHandler.error` 只收 `Exception`，`Error` 需包成 `RuntimeException` 才能走現有 `error()`。
- `FIND_ACCOUNT_SUMMARY` 在 `accountSummaryActive` 時略過重送是既有語意，本設計不改。

## Goals / Non-Goals

**Goals:**

- 單一 tick / handler / `Error` 不得終止 inbound 執行緒。
- 炸掉當筆之後繼續抽乾已在佇列的訊息，避免卡到下一筆 socket 資料才恢復。
- tick 分派不再依賴 `$SwitchMap` 合成類。
- 即時與延遲同源欄位行為對齊，未知類型只打 log。

**Non-Goals:**

- 不改 `FIND_ACCOUNT_SUMMARY` 的略過 / 強制重訂。
- 不加 inbound 閒置 watchdog（夜盤無 tick 易誤重連）。
- 不把 `start.sh` 改成 `mvn clean compile`。
- 不處理 10167 延遲行情訂閱警告。
- 不改 `AccountSummaryHandler` 的 enum switch（非此次爆點）。
- 不把匿名 `Consumer`（`$1..$3`）改成 lambda。

## Decisions

### 1. 在 avalok 子類捕捉 `Throwable`，不改 `com.ib`

- **選擇**：`ResilientApiController` 覆寫 `connect()`，自行啟動 `EReader` + `InboundMsgLoop.drain`（`Error` 包成 `RuntimeException("IB message loop", t)` 再呼叫 `error()`）；執行緒命名 `IB-Msg-Processor`。不呼叫 `super.connect()`，因此 vendored `startMsgProcessingThread` 不會跑。`IBApiController` 改 new 這個子類。
- **理由**：任何 handler 未捕捉的失敗都走同一條 inbound 執行緒，但 `com.ib` 是 TWS API 原始碼，升級時不該帶私有 patch。
- **替代**：改 vendored `ApiController.startMsgProcessingThread` → 否決（使用者要求不碰 `com.ib`）。只改 `tickPrice` try/catch → 覆蓋面不夠。

訊息迴圈形狀：

```
等待信號
迴圈:
  嘗試 processMsgs(); 成功則離開內圈
  捕捉 IOException -> error; 離開內圈
  捕捉 Throwable -> error; 繼續抽佇列
```

`processMsgs` 成功（含佇列已空）才離開內圈回去等信號。回調丟例外時當筆已出隊，繼續抽會處理剩餘佇列，不會對同一筆空轉。

### 2. TickType 用 `==` if-else，不用 `switch (tickType.index())`

- **選擇**：`tickType == TickType.BID || tickType == TickType.DELAYED_BID` 這類比對；把即時與延遲同源分支合併。
- **理由**：enum `switch` 會生成 `$SwitchMap`；`tickType.index()` 的 case 標籤不能寫 `TickType.BID.index()`（非編譯期常數），只能寫魔法數字。`==` 無合成類、無魔法數字。
- **替代**：保留 enum switch、靠 `mvn clean` 保證 `$4.class` 在 → 否決，只修觸發器、不修「執行緒會死」。用整數 switch、`66` 代表延遲買價 → 否決，可讀性差。

須保留「明確忽略、不打預設 log」的類型（例如 `OPEN` / `HALTED` / 殖利率），避免行為回歸。

`OptionTopMktDataHandler` 的 `tickPrice` / `tickSize` / `tickString` / `tickOptionComputation` 一併改，避免期權路徑留下同一個合成類炸彈。

### 3. 不在本次改帳戶摘要略過邏輯

09/11 與 09/14 的 `skip req 10001189` 字面相同；健康時略過代表訂閱仍活著。真正差別是 09/14 inbound 已死。修好迴圈後略過語意正確，不必變成每次 FIND 都取消再重訂。

## Risks / Trade-offs

- [壞訊息每筆都丟例外] → 緩解：當筆已出隊，迴圈繼續抽佇列；會打大量 `error()` log，但帳務通道仍在。不在本次做熔斷。
- [捕捉 `Error` 可能掩蓋記憶體不足 / 堆疊溢位] → 緩解：仍呼叫 `error()` 留下堆疊；記憶體不足之後 JVM 本來就不穩，比 inbound 靜默死掉卻顯示已連線更容易被發現。
- [覆寫 `connect()` 漏掉 IB 原版副作用] → 緩解：只重做 `eConnect` + 訊息執行緒 + `sendEOM()`（`sendEOM` 為 protected）。升級 TWS API 時核對 `ApiController.connect()` 是否多了步驟。
- [if-else 漏掉某個原本靜默的 enum] → 緩解：對照現有 switch 的空分支列出忽略清單；未知類型走 log，不丟例外。

## Migration Plan

- 佈署後重啟 gateway JVM（長跑舊執行緒不會載入新 class）。
- 回滾：還原三個 Java 檔並重啟。
- 驗證：`SUB_TOP_MARK` 後 tick 繼續；人為讓 tick 回調失敗時帳戶市值 / 摘要仍更新；`FIND_ACCOUNT_SUMMARY` 在已訂閱時仍略過並 ACK。
