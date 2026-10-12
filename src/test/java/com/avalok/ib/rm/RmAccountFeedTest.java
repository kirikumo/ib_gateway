package com.avalok.ib.rm;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import com.ib.client.Contract;
import com.ib.client.Decimal;
import com.ib.controller.Position;

public class RmAccountFeedTest {

	@Test
	public void assemblesSnapshotAndSkipsUnusableRows() {
		MemoryRmStore store = new MemoryRmStore();
		RmAccountFeed feed = new RmAccountFeed(store);
		feed.beginRound("DU1");

		feed.onPortfolio(pos(fut("HKFE", "", "20260728", 77, "1"), "1", 20000, 100));
		feed.onPortfolio(pos(stk("NASDAQ", "NASDAQ", "AAPL", 1), "2", 150, 300));
		Contract smart = stk("SMART", "SMART", "IBM", 55);
		feed.onPortfolio(pos(smart, "1.25", 10, 12.5));
		feed.onPortfolio(pos(fut("HKFE", "", "", 88, "1"), "1", 10, 10));
		feed.onPortfolio(pos(fut("SMART", "", "", 89, "1"), "1", 10, 10));
		feed.onPortfolio(pos(fut("HKFE", "", "", 0, "1"), "1", 10, 10));
		feed.onPortfolio(pos(stk("NYSE", "NYSE", "ZERO", 2), "0", 10, 10));
		Contract bag = new Contract();
		bag.secType("BAG");
		bag.symbol("BAG");
		bag.currency("USD");
		bag.conid(9);
		feed.onPortfolio(pos(bag, "1", 10, 10));
		feed.onPortfolio(pos(stk("NYSE", "NYSE", "MSFT", 3), "1", 0d, 99));

		feed.onValue("DU1", "CashBalance", "20.5", "EUR");
		feed.onValue("DU1", "CashBalance", "9", "BASE");
		feed.onValue("DU1", "CashBalance", "3", "");
		feed.onValue("DU1", "NetLiquidation", "999", "USD-S");
		feed.onValue("DU1", "NetLiquidation", "1000", "HKD");
		Assert.assertTrue(feed.isRoundWritable());
		feed.onDownloadEnd("DU1", 5000L);

		String balanceJson = store.get(RmKeys.balance("DU1"));
		String summaryJson = store.get(RmKeys.summary("DU1"));
		Assert.assertNotNull(balanceJson);
		Assert.assertFalse(balanceJson.contains("openCount"));
		Assert.assertFalse(balanceJson.contains("BAG"));
		Assert.assertTrue(balanceJson.contains("\"marketPrice\":null"));
		Assert.assertTrue(balanceJson.contains("\"marketValue\":null"));

		Map<String, Object> balance = RmJson.parse(balanceJson);
		Map<String, Object> summary = RmJson.parse(summaryJson);
		Assert.assertEquals(5000L, lng(balance.get("updateTimeMs")));
		Assert.assertEquals(lng(balance.get("updateTimeMs")), lng(summary.get("updateTimeMs")));
		Assert.assertEquals("HKD", summary.get("baseCurrency"));
		Assert.assertEquals(0, num(summary.get("netLiquidation")).compareTo(new BigDecimal("1000")));

		@SuppressWarnings("unchecked")
		Map<String, Object> cash = (Map<String, Object>) balance.get("cash");
		Assert.assertTrue(cash.containsKey("EUR"));
		Assert.assertFalse(cash.containsKey("BASE"));
		Assert.assertEquals(0, num(map(cash.get("EUR")).get("balance")).compareTo(new BigDecimal("20.5")));

		@SuppressWarnings("unchecked")
		Map<String, Object> positions = (Map<String, Object>) balance.get("positions");
		Set<String> keys = new HashSet<>(positions.keySet());
		Assert.assertTrue(keys.contains("HKFE:FUT:HKD-HSI@20260728@1"));
		Assert.assertTrue(keys.contains("NASDAQ:STK:USD-AAPL"));
		Assert.assertTrue(keys.contains("STK:conid:55"));
		Assert.assertTrue(keys.contains("HKFE:FUT:conid:88"));
		Assert.assertTrue(keys.contains("FUT:conid:89"));
		Assert.assertEquals(6, keys.size());
		for (String key : keys) {
			if (key.startsWith("HKFE:FUT:HKD")) Assert.assertTrue(key.startsWith("HKFE:"));
		}
		Assert.assertEquals(0, num(map(positions.get("STK:conid:55")).get("pos")).compareTo(new BigDecimal("1.25")));
		Map<String, Object> unpriced = map(positions.get("NYSE:STK:USD-MSFT"));
		Assert.assertNull(unpriced.get("marketPrice"));
		Assert.assertNull(unpriced.get("marketValue"));
	}

	@Test
	public void accountReadyFalseIsNotWritableAndDoesNotSet() {
		MemoryRmStore store = new MemoryRmStore();
		RmAccountFeed feed = new RmAccountFeed(store);
		feed.beginRound("DU1");
		feed.onValue("DU1", "AccountReady", "false", null);
		feed.onValue("DU1", "NetLiquidation", "1000", "HKD");
		feed.onValue("DU1", "CashBalance", "20", "EUR");
		Assert.assertFalse(feed.isRoundWritable());
		feed.onDownloadEnd("DU1", 5000L);
		Assert.assertNull(store.get(RmKeys.balance("DU1")));
		Assert.assertNull(store.get(RmKeys.summary("DU1")));
	}

	@Test
	public void missingNetLiquidationDoesNotSet() {
		MemoryRmStore store = new MemoryRmStore();
		RmAccountFeed feed = new RmAccountFeed(store);
		feed.beginRound("DU1");
		feed.onValue("DU1", "CashBalance", "20", "EUR");
		feed.onValue("DU1", "NetLiquidation", "999", "USD-S");
		feed.onDownloadEnd("DU1", 5000L);
		Assert.assertNull(store.get(RmKeys.balance("DU1")));
		Assert.assertNull(store.get(RmKeys.summary("DU1")));
	}

	@Test
	public void baseNetLiquidationIsKept() {
		MemoryRmStore store = new MemoryRmStore();
		RmAccountFeed feed = new RmAccountFeed(store);
		feed.beginRound("DU1");
		feed.onValue("DU1", "NetLiquidation", "80", "BASE");
		feed.onDownloadEnd("DU1", 42L);
		Map<String, Object> summary = RmJson.parse(store.get(RmKeys.summary("DU1")));
		Assert.assertEquals("BASE", summary.get("baseCurrency"));
		Assert.assertEquals(42L, lng(summary.get("updateTimeMs")));
	}

	private static Position pos(Contract contract, String qty, double price, double value) {
		return new Position(contract, "DU1", Decimal.get(new BigDecimal(qty)), price, value, 0, 0, 0);
	}

	private static Contract stk(String exchange, String primary, String symbol, int conId) {
		Contract c = new Contract();
		c.secType("STK");
		c.symbol(symbol);
		c.currency("USD");
		c.exchange(exchange);
		c.primaryExch(primary);
		c.conid(conId);
		return c;
	}

	private static Contract fut(String exchange, String primary, String date, int conId, String multiplier) {
		Contract c = new Contract();
		c.secType("FUT");
		c.symbol("HSI");
		c.currency("HKD");
		c.exchange(exchange);
		c.primaryExch(primary);
		c.conid(conId);
		c.multiplier(multiplier);
		if (date != null && !date.isEmpty()) c.lastTradeDate(date);
		return c;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> map(Object v) {
		return (Map<String, Object>) v;
	}

	private static long lng(Object v) {
		return ((Number) v).longValue();
	}

	private static BigDecimal num(Object v) {
		if (v instanceof BigDecimal) return (BigDecimal) v;
		return new BigDecimal(v.toString());
	}
}
