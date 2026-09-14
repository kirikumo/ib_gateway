package com.avalok.ib.controller;

import java.io.IOException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * IB inbound 抽佇列：單筆 callback 的 Error 不得終止迴圈。
 */
final class InboundMsgLoop {
	interface Batch {
		void processMsgs() throws IOException;
	}

	static void drain(BooleanSupplier connected, Batch batch, Consumer<Exception> onError) {
		while (connected.getAsBoolean()) {
			try {
				batch.processMsgs();
				return;
			} catch (IOException e) {
				onError.accept(e);
				return;
			} catch (Throwable t) {
				onError.accept(t instanceof Exception ? (Exception) t : new RuntimeException("IB message loop", t));
			}
		}
	}

	private InboundMsgLoop() {}
}
