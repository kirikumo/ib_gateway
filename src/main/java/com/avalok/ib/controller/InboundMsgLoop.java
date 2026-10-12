package com.avalok.ib.controller;

import java.io.IOException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Drains the IB inbound queue. An Error from one callback must not stop the loop.
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
