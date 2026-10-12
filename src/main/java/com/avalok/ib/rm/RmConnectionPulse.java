package com.avalok.ib.rm;

import java.util.LinkedHashMap;
import java.util.Map;

/** Risk connection key written from the one-second heartbeat. This method does not sleep. */
public final class RmConnectionPulse {
	static final long INTERVAL_MS = 5_000L;
	private boolean wrote;
	private long lastRm;

	/**
	 * @return status JSON, published as-is on the existing ACK channel
	 */
	public String tick(long now, boolean realConnected, boolean apiConnected, String gateway, RmStore store) {
		Map<String, Object> status = new LinkedHashMap<>();
		status.put("type", "heartbeat");
		status.put("status", apiConnected);
		status.put("t", now);
		String statusJson = RmJson.toJson(status);
		store.setex("IBGateway:" + gateway + ":status", 2764800, statusJson);
		if (realConnected) {
			store.setex(gateway + "_CONNECTION", 2, "Not None");
			if (!wrote || now - lastRm >= INTERVAL_MS) {
				Map<String, Object> conn = new LinkedHashMap<>();
				conn.put("updateTimeMs", now);
				store.setex(RmKeys.connection(gateway), 15, RmJson.toJson(conn));
				wrote = true;
				lastRm = now;
			}
		}
		return statusJson;
	}
}
