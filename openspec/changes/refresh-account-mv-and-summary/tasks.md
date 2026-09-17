## 1. 帳戶摘要增量寫 Redis

- [x] 1.1 在 `AccountSummaryHandler` 加入 snapshot 完成旗標：`accountSummary()` 先更新 `m_map`，旗標為真才寫該帳戶 `IBGateway:Summary:{account}`（`data` + `updateTime`）；`accountSummaryEnd()` 先設旗標再寫全部帳戶。提供開始新 snapshot 時清旗標的入口。單元測試（不連 TWS）：End 前的欄位回調不觸發寫入；End 後的欄位回調寫該帳戶且 `updateTime` 為寫入當下；清旗標後到下一次 End 前再次不寫。若測試環境無 Redis，用可注入的寫入出口或記錄寫入次數的測試替身。
- [x] 1.2 `queryAccountSummary` 在真正 `reqAccountSummary` 之前清 snapshot 旗標；`FIND_ACCOUNT_SUMMARY` 已訂閱的 skip 路徑不得清旗標、不得重送。`grep` `queryAccountSummary`：skip 分支仍有 `already subscribed, skip`；非 skip 路徑在 `reqAccountSummary` 之前有清旗標。

## 2. 市值刷新互斥與 worker

- [x] 2.1 抽出單一市值刷新入口：連線後、定時、`UPDATE_ACCOUNT_MV` 共用 `AtomicBoolean`（或同等）互斥；`reqAccountUpdates` + 帳戶間 `sleep(3000)` 只在 worker 執行緒執行；拿不到鎖則略過並打 log；結束時若 `focusAccount` 不是最後一個帳則再訂一次；斷線則結束本輪並釋放互斥。`grep` `subscribeAccountMV` 與 `reqAccountUpdates` 不得在呼叫執行緒 `sleep`。
- [x] 2.2 `_postConnected` 改為非阻塞觸發市值刷新，隨後仍呼叫 `queryAccountSummary`、`refreshLiveOrders`、`refreshCompletedOrders`。`grep` `_postConnected` 內 `subscribeAccountMV`（或新入口）之後仍立即有 `queryAccountSummary`，且該方法本身不再 `sleep(3000)`。

## 3. 獨立 30 秒 Timer

- [x] 3.1 新增獨立於 `GatewayControllerLiveStatusWriter` 的 daemon `Timer`，延遲 30 秒、週期 30 秒：已連線且拿到互斥才跑一輪，否則略過。`grep` `GatewayController.java`：心跳 Timer 不得呼叫市值入口；新 Timer 週期為 `30000`；不得把市值 `sleep` 掛進 LiveStatusWriter。

## 4. 回歸

- [x] 4.1 `UPDATE_FOCUS_ACCOUNT` 仍先寫 `focusAccount`，無進行中輪詢時直接 `reqAccountUpdates`；`FIND_ACCOUNT_SUMMARY` skip 與 ACK 不變。`grep` 這兩個 case 仍在，且無定時 `queryAccountSummary`。
- [x] 4.2 `mvn test` 通過。若有 TWS：連線後 balance／Summary 有值；30 秒後 log 再出現 `Req account mv`；摘要 End 後欄位回調會更新該帳戶 `updateTime`；FIND 已訂閱仍 skip。無 TWS 則在本任務註記未覆蓋的現場路徑。
  未覆蓋：真實 TWS 連線下連線後寫入 `IBGateway:{account}:balance`／`IBGateway:Summary:{account}`、30 秒後再出現 `Req account mv`、摘要增量更新 `updateTime`、FIND 已訂閱仍 skip。已用 `JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 mvn test`：Tests run: 20, Failures: 0, Errors: 0。含 `AccountSummaryHandlerRedisTest` 三則（End 前不寫、End 後增量寫、beginSnapshot 清旗標）。
