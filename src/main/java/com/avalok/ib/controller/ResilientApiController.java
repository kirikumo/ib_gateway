package com.avalok.ib.controller;

import com.ib.client.Decimal;
import com.ib.client.EJavaSignal;
import com.ib.client.EReader;
import com.ib.client.EReaderSignal;
import com.ib.controller.ApiConnection.ILogger;
import com.ib.controller.ApiController;

/**
 * Leave the vendored ApiController alone. Override connect() and replace the private
 * startMsgProcessingThread with an inbound thread that survives Error.
 */
final class ResilientApiController extends ApiController {
	static volatile RawOrderStatusListener rawOrderStatusListener;

	ResilientApiController(IConnectionHandler handler, ILogger inLogger, ILogger outLogger) {
		super(handler, inLogger, outLogger);
	}

	@Override
	public void orderStatus(int orderId, String status, Decimal filled, Decimal remaining, double avgFillPrice,
			long permId, int parentId, double lastFillPrice, int clientId, String whyHeld, double mktCapPrice) {
		if (status == null) return;
		try {
			super.orderStatus(orderId, status, filled, remaining, avgFillPrice, permId, parentId, lastFillPrice, clientId, whyHeld, mktCapPrice);
		} catch (IllegalArgumentException ex) {
			// OrderStatus.valueOf has no PreCancelled, Rejected, WarnState, or Canceled.
			RawOrderStatusListener listener = rawOrderStatusListener;
			if (listener != null)
				listener.onRawOrderStatus(orderId, status, filled, remaining, avgFillPrice, permId);
		}
	}

	@Override
	public void connect(String host, int port, int clientId, String connectOptions) {
		if (client().isConnected())
			return;
		client().setConnectOptions(connectOptions);
		client().eConnect(host, port, clientId);
		startResilientMsgThread();
		sendEOM();
	}

	private void startResilientMsgThread() {
		final EReaderSignal signal = new EJavaSignal();
		final EReader reader = new EReader(client(), signal);
		reader.start();
		new Thread(() -> {
			while (client().isConnected()) {
				signal.waitForSignal();
				InboundMsgLoop.drain(client()::isConnected, reader::processMsgs, this::error);
			}
		}, "IB-Msg-Processor").start();
	}
}
