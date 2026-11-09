package com.avalok.ib.handler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.alibaba.fastjson.JSONObject;
import com.avalok.ib.IBContract;
import com.avalok.ib.rm.MemoryRmStore;
import com.avalok.ib.rm.RmAccountFeed;
import com.avalok.ib.rm.RmJson;
import com.avalok.ib.rm.RmKeys;
import com.ib.client.Contract;
import com.ib.client.Decimal;
import com.ib.controller.Position;

public class AccountMVHandlerRmTest {

	private final List<String> oldKeys = new ArrayList<>();
	private final List<Set<String>> books = new ArrayList<>();
	private final List<String> knownKeys = new ArrayList<>();
	private MemoryRmStore store;
	private RmAccountFeed feed;
	private AccountMVHandler handler;

	@Before
	public void setUp() {
		oldKeys.clear();
		books.clear();
		store = new MemoryRmStore();
		feed = new RmAccountFeed(store);
		handler = new AccountMVHandler();
		handler.setBalanceWriter((key, ttl, value) -> {
			Assert.assertEquals(2764800, ttl);
			oldKeys.add(key);
			@SuppressWarnings("unchecked")
			Map<String, ?> pos = (Map<String, ?>) value;
			books.add(new HashSet<>(pos.keySet()));
		});
		handler.setRmFeed(feed);
	}

	@After
	public void tearDown() {
		for (String known : knownKeys)
			ContractDetailsHandler.KNOWN_CONTRACTS.remove(known);
		knownKeys.clear();
	}

	@Test
	public void portfolioBeforeEndWritesNeitherKey() {
		handler.updatePortfolio(position("AAPL", "NASDAQ", 1));
		Assert.assertTrue(oldKeys.isEmpty());
		Assert.assertNull(store.get(RmKeys.balance("DU1")));
	}

	@Test
	public void successfulEndSharesTimestampAndIncrementalPortfolioKeepsRm() {
		feed.beginRound("DU1");
		handler.updatePortfolio(position("AAPL", "NASDAQ", 1));
		handler.accountValue("DU1", "CashBalance", "20", "EUR");
		handler.accountValue("DU1", "CashBalance", "9", "BASE");
		handler.accountValue("DU1", "NetLiquidation", "1000", "HKD");
		Assert.assertTrue(oldKeys.isEmpty());
		Assert.assertNull(store.get(RmKeys.balance("DU1")));

		handler.accountDownloadEnd("DU1");
		Assert.assertEquals(List.of("IBGateway:DU1:balance"), oldKeys);
		String balance = store.get(RmKeys.balance("DU1"));
		String summary = store.get(RmKeys.summary("DU1"));
		Assert.assertEquals(
				RmJson.parse(balance).get("updateTimeMs"),
				RmJson.parse(summary).get("updateTimeMs"));
		Assert.assertTrue(balance.contains("EUR"));
		Assert.assertFalse(balance.contains("BASE"));
		Assert.assertTrue(balance.contains("NASDAQ:STK:USD-AAPL"));

		feed.beginRound("DU1");
		handler.updatePortfolio(position("MSFT", "NASDAQ", 2));
		Assert.assertEquals(1, oldKeys.size());
		Assert.assertEquals(balance, store.get(RmKeys.balance("DU1")));

		handler.accountDownloadEnd("DU1");
		Assert.assertEquals(2, oldKeys.size());
		Assert.assertEquals("IBGateway:DU1:balance", oldKeys.get(1));
	}

	@Test
	public void otherAccountEndWritesOnlyThatBalance() {
		feed.beginRound("A");
		handler.updatePortfolio(position("A", "AAPL", "NASDAQ", 1));
		handler.accountValue("A", "CashBalance", "20", "EUR");
		handler.accountDownloadEnd("A");
		Assert.assertEquals(List.of("IBGateway:A:balance"), oldKeys);

		feed.beginRound("B");
		handler.updatePortfolio(position("B", "MSFT", "NASDAQ", 2));
		handler.updatePortfolio(position("B", "IBM", "NYSE", 3));
		handler.accountValue("B", "CashBalance", "5", "USD");
		Assert.assertEquals(List.of("IBGateway:A:balance"), oldKeys);

		handler.accountDownloadEnd("B");
		Assert.assertEquals(List.of("IBGateway:A:balance", "IBGateway:B:balance"), oldKeys);
	}

	@Test
	public void accountReadyFalseEndKeepsPreviousRmAndStillWritesOldBalance() {
		feed.beginRound("DU1");
		handler.accountValue("DU1", "CashBalance", "20", "EUR");
		handler.accountValue("DU1", "AccountReady", "false", null);
		handler.accountValue("DU1", "NetLiquidation", "1000", "HKD");
		Assert.assertFalse(feed.isRoundWritable());
		handler.accountDownloadEnd("DU1");
		Assert.assertEquals(List.of("IBGateway:DU1:balance"), oldKeys);
		Assert.assertNull(store.get(RmKeys.balance("DU1")));
		Assert.assertNull(store.get(RmKeys.summary("DU1")));
	}

	@Test
	public void blankExchangeBorrowsListingAndSkipsUnknown() {
		remember(hkStock(269492179, "7200", "SEHK", null));
		handler.updatePortfolio(hkPosition("7200", null, 269492179, 14900, 75031.57));
		handler.updatePortfolio(hkPosition("5", "", 5, 1, 1));
		handler.accountValue("DU1", "CashBalance", "20", "HKD");
		handler.accountDownloadEnd("DU1");

		Set<String> book = books.get(0);
		Assert.assertTrue(book.contains("SEHK:STK:HKD-7200"));
		Assert.assertFalse(book.contains("null:STK:HKD-7200"));
		Assert.assertFalse(book.contains("null:STK:HKD-5"));
		Assert.assertFalse(book.contains(":STK:HKD-5"));
		Assert.assertTrue(book.contains("HKD"));
		Assert.assertFalse(handler.ibc_cache.containsKey("HKD-5"));
		Assert.assertTrue(handler.ibc_cache.containsKey("HKD-7200"));
	}

	@Test
	public void smartExchangeIsNotReborrowed() {
		remember(hkStock(700, "700", "SEHK", null));
		handler.updatePortfolio(hkPosition("700", "SMART", 700, 1, 1));
		handler.accountDownloadEnd("DU1");
		Assert.assertTrue(books.get(0).contains("SMART:STK:HKD-700"));
		Assert.assertFalse(books.get(0).contains("SEHK:STK:HKD-700"));
	}

	@Test
	public void unknownBlankRowLeavesExistingListing() {
		handler.updatePortfolio(hkPosition("5", "SEHK", 5, 1, 10));
		handler.accountValue("DU1", "CashBalance", "20", "HKD");
		handler.accountDownloadEnd("DU1");

		handler.updatePortfolio(hkPosition("7200", null, 269492179, 14900, 75031.57));
		handler.accountDownloadEnd("DU1");

		Set<String> book = books.get(1);
		Assert.assertTrue(book.contains("SEHK:STK:HKD-5"));
		Assert.assertTrue(book.contains("HKD"));
		Assert.assertFalse(book.contains("null:STK:HKD-7200"));
		Assert.assertFalse(book.contains("SEHK:STK:HKD-7200"));
	}

	@Test
	public void laterKnownContractFillsTheSkippedRow() {
		handler.updatePortfolio(hkPosition("7200", null, 269492179, 1, 1));
		handler.accountValue("DU1", "CashBalance", "20", "HKD");
		handler.accountDownloadEnd("DU1");
		Assert.assertTrue(books.get(0).contains("HKD"));
		Assert.assertFalse(books.get(0).contains("null:STK:HKD-7200"));
		Assert.assertFalse(books.get(0).contains("SEHK:STK:HKD-7200"));

		remember(hkStock(269492179, "7200", "SEHK", null));
		handler.updatePortfolio(hkPosition("7200", null, 269492179, 14900, 75031.57));
		handler.accountDownloadEnd("DU1");
		Assert.assertTrue(books.get(1).contains("SEHK:STK:HKD-7200"));
		Assert.assertFalse(books.get(1).contains("null:STK:HKD-7200"));
	}

	@Test
	public void writeKeepsStaleNullKeyUntilRestart() {
		handler.updatePortfolio(hkPosition("7200", "SEHK", 269492179, 1, 10));
		handler.accountValue("DU1", "CashBalance", "20", "HKD");
		handler.accountDownloadEnd("DU1");

		handler.position().get("DU1").put("null:STK:HKD-7200", new JSONObject());
		handler.accountDownloadEnd("DU1");

		Set<String> book = books.get(1);
		Assert.assertTrue(book.contains("SEHK:STK:HKD-7200"));
		Assert.assertTrue(book.contains("HKD"));
		Assert.assertTrue(book.contains("null:STK:HKD-7200"));
	}

	private void remember(IBContract ibc) {
		String key = ibc.shownName();
		ContractDetailsHandler.KNOWN_CONTRACTS.put(key, ibc);
		knownKeys.add(key);
	}

	private static IBContract hkStock(int conid, String symbol, String exchange, String primary) {
		Contract c = new Contract();
		c.conid(conid);
		c.symbol(symbol);
		c.secType("STK");
		c.currency("HKD");
		c.exchange(exchange);
		c.primaryExch(primary);
		return new IBContract(c);
	}

	private static Position hkPosition(String symbol, String exchange, int conId, int qty, double marketValue) {
		Contract contract = new Contract();
		contract.symbol(symbol);
		contract.secType("STK");
		contract.currency("HKD");
		contract.exchange(exchange);
		contract.conid(conId);
		return new Position(contract, "DU1", Decimal.get(qty), 1, marketValue, 1, 0, 0);
	}

	private static Position position(String symbol, String exchange, int conId) {
		return position("DU1", symbol, exchange, conId);
	}

	private static Position position(String account, String symbol, String exchange, int conId) {
		Contract contract = new Contract();
		contract.symbol(symbol);
		contract.secType("STK");
		contract.currency("USD");
		contract.exchange(exchange);
		contract.primaryExch(exchange);
		contract.conid(conId);
		return new Position(contract, account, Decimal.get(1), 150, 150, 10, 0, 0);
	}
}
