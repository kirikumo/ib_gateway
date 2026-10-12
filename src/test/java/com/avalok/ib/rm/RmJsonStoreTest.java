package com.avalok.ib.rm;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class RmJsonStoreTest {

	@Test
	public void nullPriceSurvivesHsetAndPublishAsTheSameString() {
		Map<String, Object> doc = new LinkedHashMap<>();
		doc.put("orderRef", "uranus_1");
		doc.put("price", null);
		String json = RmJson.toJson(doc);

		MemoryRmStore store = new MemoryRmStore();
		store.hset("RM:IBKR:DU1:open", "uranus_1", json);
		store.publish("RM:IBKR:DU1:order", json);

		Assert.assertTrue(json.startsWith("{"));
		Assert.assertTrue(json.contains("\"price\":null"));
		Assert.assertFalse(json.startsWith("\""));

		String stored = store.peek("RM:IBKR:DU1:open", "uranus_1");
		Assert.assertEquals(json, stored);
		MemoryRmStore.Op published = null;
		for (MemoryRmStore.Op op : store.snapshot()) {
			if ("PUBLISH".equals(op.kind)) published = op;
		}
		Assert.assertNotNull(published);
		Assert.assertEquals(stored, published.value);

		Map<String, Object> parsed = RmJson.parse(stored);
		Assert.assertTrue(parsed.containsKey("price"));
		Assert.assertNull(parsed.get("price"));
		Assert.assertEquals("uranus_1", parsed.get("orderRef"));

		Map<String, String> all = store.hgetAll("RM:IBKR:DU1:open");
		Assert.assertEquals(json, all.get("uranus_1"));
		all.put("uranus_1", "mutated");
		Assert.assertEquals(json, store.peek("RM:IBKR:DU1:open", "uranus_1"));

		store.hdel("RM:IBKR:DU1:open", "uranus_1");
		Assert.assertNull(store.peek("RM:IBKR:DU1:open", "uranus_1"));
		Assert.assertTrue(store.hgetAll("RM:IBKR:DU1:open").isEmpty());
	}
}
