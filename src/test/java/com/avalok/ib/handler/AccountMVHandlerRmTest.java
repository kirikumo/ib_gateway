package com.avalok.ib.handler;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.avalok.ib.rm.MemoryRmStore;
import com.avalok.ib.rm.RmAccountFeed;
import com.avalok.ib.rm.RmJson;
import com.avalok.ib.rm.RmKeys;
import com.ib.client.Contract;
import com.ib.client.Decimal;
import com.ib.controller.Position;

public class AccountMVHandlerRmTest {

	private final List<String> oldKeys = new ArrayList<>();
	private MemoryRmStore store;
	private RmAccountFeed feed;
	private AccountMVHandler handler;

	@Before
	public void setUp() {
		oldKeys.clear();
		store = new MemoryRmStore();
		feed = new RmAccountFeed(store);
		handler = new AccountMVHandler();
		handler.setBalanceWriter((key, ttl, value) -> oldKeys.add(key));
		handler.setRmFeed(feed);
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
		Assert.assertEquals(2, oldKeys.size());
		Assert.assertEquals("IBGateway:DU1:balance", oldKeys.get(1));
		Assert.assertEquals(balance, store.get(RmKeys.balance("DU1")));
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

	private static Position position(String symbol, String exchange, int conId) {
		Contract contract = new Contract();
		contract.symbol(symbol);
		contract.secType("STK");
		contract.currency("USD");
		contract.exchange(exchange);
		contract.primaryExch(exchange);
		contract.conid(conId);
		return new Position(contract, "DU1", Decimal.get(1), 150, 150, 10, 0, 0);
	}
}
