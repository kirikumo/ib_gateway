package com.avalok.ib.handler;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.alibaba.fastjson.JSONObject;
import com.avalok.ib.GatewayController;
import com.avalok.ib.IBContract;

public class ContractDetailsHandlerQueryTest {

	private static final String KEY = "SEHK:STK:HKD-1";

	private GatewayController savedController;

	@Before
	public void setUp() {
		savedController = ContractDetailsHandler.GW_CONTROLLER;
		ContractDetailsHandler.GW_CONTROLLER = null;
		ContractDetailsHandler.KNOWN_CONTRACT_DETAILS.remove(KEY);
		ContractDetailsHandler.QUERY_HIS.remove(KEY);
	}

	@After
	public void tearDown() {
		ContractDetailsHandler.GW_CONTROLLER = savedController;
		ContractDetailsHandler.KNOWN_CONTRACT_DETAILS.remove(KEY);
		ContractDetailsHandler.QUERY_HIS.remove(KEY);
	}

	@Test
	public void freshDetailSkipsQueryWithoutController() {
		long now = System.currentTimeMillis();
		JSONObject known = new JSONObject();
		known.put("_timestamp", now);
		ContractDetailsHandler.KNOWN_CONTRACT_DETAILS.put(KEY, known);

		ContractDetailsHandler.queryDetails(named(KEY));

		Assert.assertNull(ContractDetailsHandler.QUERY_HIS.get(KEY));
		Assert.assertTrue(ContractDetailsHandler.cachedFresh(KEY, now));
	}

	@Test
	public void missingTimestampNeedsQuery() {
		ContractDetailsHandler.KNOWN_CONTRACT_DETAILS.put(KEY, new JSONObject());
		Assert.assertFalse(ContractDetailsHandler.cachedFresh(KEY, System.currentTimeMillis()));
		Assert.assertNull(ContractDetailsHandler.QUERY_HIS.get(KEY));
	}

	@Test
	public void dayOldDetailNeedsQuery() {
		long now = System.currentTimeMillis();
		JSONObject known = new JSONObject();
		known.put("_timestamp", now - ContractDetailsHandler.KNOWN_CONTRACT_REFRESH_MS);
		ContractDetailsHandler.KNOWN_CONTRACT_DETAILS.put(KEY, known);
		Assert.assertFalse(ContractDetailsHandler.cachedFresh(KEY, now));
		Assert.assertNull(ContractDetailsHandler.QUERY_HIS.get(KEY));
	}

	private static IBContract named(String shownName) {
		return new IBContract() {
			@Override
			public String shownName() {
				return shownName;
			}
		};
	}
}
