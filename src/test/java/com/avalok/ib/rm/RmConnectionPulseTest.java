package com.avalok.ib.rm;

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class RmConnectionPulseTest {

	@Test
	public void connectionKeyEveryFiveSecondsWhileReallyConnected() {
		MemoryRmStore store = new MemoryRmStore();
		RmConnectionPulse pulse = new RmConnectionPulse();
		String first = pulse.tick(1000L, true, true, "gw1", store);
		pulse.tick(5999L, true, true, "gw1", store);
		Assert.assertEquals(1, count(store, "SETEX", RmKeys.connection("gw1")));
		pulse.tick(6000L, true, true, "gw1", store);
		Assert.assertEquals(2, count(store, "SETEX", RmKeys.connection("gw1")));

		pulse.tick(7000L, false, false, "gw1", store);
		Assert.assertEquals(2, count(store, "SETEX", RmKeys.connection("gw1")));
		Assert.assertEquals(3, count(store, "SETEX", "gw1_CONNECTION"));
		Assert.assertEquals(4, count(store, "SETEX", "IBGateway:gw1:status"));

		int connectionSeen = 0;
		for (MemoryRmStore.Op op : store.snapshot()) {
			if ("SETEX".equals(op.kind) && RmKeys.connection("gw1").equals(op.key)) {
				connectionSeen++;
				Assert.assertEquals(15, op.ttl);
				Map<String, Object> doc = RmJson.parse(op.value);
				Assert.assertEquals(1, doc.size());
				Assert.assertTrue(doc.containsKey("updateTimeMs"));
			}
			if ("SETEX".equals(op.kind) && "gw1_CONNECTION".equals(op.key)) {
				Assert.assertEquals(2, op.ttl);
				Assert.assertEquals("Not None", op.value);
			}
			if ("SETEX".equals(op.kind) && "IBGateway:gw1:status".equals(op.key)) {
				Assert.assertEquals(2764800, op.ttl);
			}
		}
		Assert.assertEquals(2, connectionSeen);

		Map<String, Object> status = RmJson.parse(first);
		Assert.assertEquals("heartbeat", status.get("type"));
		Assert.assertEquals(Boolean.TRUE, status.get("status"));
		Assert.assertEquals(1000L, ((Number) status.get("t")).longValue());
		Map<String, Object> down = RmJson.parse(store.get("IBGateway:gw1:status"));
		Assert.assertEquals(Boolean.FALSE, down.get("status"));
		Assert.assertEquals(7000L, ((Number) down.get("t")).longValue());
	}

	private static int count(MemoryRmStore store, String kind, String key) {
		int n = 0;
		for (MemoryRmStore.Op op : store.snapshot()) {
			if (kind.equals(op.kind) && key.equals(op.key)) n++;
		}
		return n;
	}
}
