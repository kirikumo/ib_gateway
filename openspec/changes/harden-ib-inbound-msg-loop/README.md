# harden-ib-inbound-msg-loop

避免 IB inbound 執行緒被 `TopMktDataHandler` 的 enum-switch 合成類或缺漏的未捕捉 `Error` 打死，讓帳務與訂單回調能繼續進來。
