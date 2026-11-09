package com.avalok.ib.handler;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import com.alibaba.fastjson.JSONObject;
import com.avalok.ib.GatewayController;
import com.avalok.ib.IBContract;
import com.ib.client.Contract;

public class ContractDetailsHandlerQueryTest {

	private static final String KEY = "SEHK:STK:HKD-1";

	private GatewayController savedController;
	private final List<String> knownKeys = new ArrayList<>();

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
		for (String known : knownKeys)
			ContractDetailsHandler.KNOWN_CONTRACTS.remove(known);
		knownKeys.clear();
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

	@Test
	public void knownExchangePrefersNonSmartExchange() {
		remember(stock(269492179, "SEHK", null));
		Assert.assertEquals("SEHK", ContractDetailsHandler.knownExchange(269492179));
	}

	@Test
	public void knownExchangeUsesPrimaryWhenExchangeIsSmart() {
		remember(stock(269492179, "SMART", "SEHK"));
		Assert.assertEquals("SEHK", ContractDetailsHandler.knownExchange(269492179));
	}

	@Test
	public void knownExchangeSkipsSmartWithoutPrimary() {
		remember(stock(269492179, "SMART", null));
		Assert.assertNull(ContractDetailsHandler.knownExchange(269492179));
	}

	@Test
	public void knownExchangeIgnoresConidZero() {
		remember(stock(0, "SEHK", null));
		Assert.assertNull(ContractDetailsHandler.knownExchange(0));
	}

	@Test
	public void applyExchangeRebuildsShownName() {
		IBContract ibc = stock(269492179, null, null);
		Assert.assertEquals("null:STK:HKD-7200", ibc.shownName());
		ibc.applyExchange("SEHK");
		Assert.assertEquals("SEHK:STK:HKD-7200", ibc.shownName());
	}

	private void remember(IBContract ibc) {
		String key = ibc.shownName();
		ContractDetailsHandler.KNOWN_CONTRACTS.put(key, ibc);
		knownKeys.add(key);
	}

	private static IBContract stock(int conid, String exchange, String primary) {
		Contract c = new Contract();
		c.conid(conid);
		c.symbol("7200");
		c.secType("STK");
		c.currency("HKD");
		c.exchange(exchange);
		c.primaryExch(primary);
		return new IBContract(c);
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
