package com.avalok.ib.rm;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** updateTimeMs for one orderRef strictly increases. If none is cached, read it from the hash JSON. */
public final class RmClock {
	private final Map<String, Long> last = new ConcurrentHashMap<>();

	public long next(String orderRef, long now, String previousJson) {
		long prev = last.containsKey(orderRef) ? last.get(orderRef) : read(previousJson);
		long out = now > prev ? now : prev + 1;
		last.put(orderRef, out);
		return out;
	}

	private static long read(String json) {
		if (json == null || json.isEmpty()) return 0L;
		try {
			Object v = RmJson.parse(json).get("updateTimeMs");
			if (v instanceof Number) return ((Number) v).longValue();
		} catch (RuntimeException ignored) {
			return 0L;
		}
		return 0L;
	}
}
