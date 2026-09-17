package com.avalok.ib.handler;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.ib.controller.AccountSummaryTag;

public class AccountSummaryHandlerRedisTest {

	private final List<JSONObject> writes = new ArrayList<>();
	private AccountSummaryHandler handler;

	@Before
	public void setUp() {
		writes.clear();
		handler = new AccountSummaryHandler((key, json) -> {
			JSONObject row = JSON.parseObject(json);
			row.put("_key", key);
			writes.add(row);
		});
	}

	@Test
	public void snapshotCallbacksDoNotWriteBeforeEnd() {
		handler.accountSummary("DU1", AccountSummaryTag.NetLiquidation, "100", "USD");
		handler.accountSummary("DU1", AccountSummaryTag.BuyingPower, "400", "USD");
		Assert.assertTrue(writes.isEmpty());
	}

	@Test
	public void incrementalCallbackWritesAccountAfterEnd() {
		handler.accountSummary("DU1", AccountSummaryTag.NetLiquidation, "100", "USD");
		handler.accountSummaryEnd();
		Assert.assertEquals(1, writes.size());
		writes.clear();

		long before = System.currentTimeMillis();
		handler.accountSummary("DU1", AccountSummaryTag.BuyingPower, "390", "USD");
		Assert.assertEquals(1, writes.size());
		JSONObject row = writes.get(0);
		Assert.assertEquals("IBGateway:Summary:DU1", row.getString("_key"));
		JSONObject data = row.getJSONObject("data");
		Assert.assertEquals("100", data.getString("NetLiquidation"));
		Assert.assertEquals("390", data.getString("BuyingPower"));
		Assert.assertTrue(row.getLongValue("updateTime") >= before);
	}

	@Test
	public void beginSnapshotStopsWritesUntilNextEnd() {
		handler.accountSummary("DU1", AccountSummaryTag.NetLiquidation, "100", "USD");
		handler.accountSummaryEnd();
		handler.beginSnapshot();
		writes.clear();

		handler.accountSummary("DU1", AccountSummaryTag.BuyingPower, "350", "USD");
		Assert.assertTrue(writes.isEmpty());

		handler.accountSummaryEnd();
		Assert.assertEquals(1, writes.size());
		JSONObject data = writes.get(0).getJSONObject("data");
		Assert.assertEquals("350", data.getString("BuyingPower"));
	}
}
