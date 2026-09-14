package com.avalok.ib.controller;

import com.ib.client.EJavaSignal;
import com.ib.client.EReader;
import com.ib.client.EReaderSignal;
import com.ib.controller.ApiConnection.ILogger;
import com.ib.controller.ApiController;

/**
 * 不改 vendored ApiController：覆寫 connect()，用可承受 Error 的 inbound 執行緒取代 private startMsgProcessingThread。
 */
final class ResilientApiController extends ApiController {
	ResilientApiController(IConnectionHandler handler, ILogger inLogger, ILogger outLogger) {
		super(handler, inLogger, outLogger);
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
