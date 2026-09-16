package com.avalok.ib.handler;

import org.junit.Assert;
import org.junit.Test;

import com.avalok.ib.IBContract;
import com.ib.client.Decimal;
import com.ib.client.TickType;
import com.ib.client.Types.DeepSide;
import com.ib.client.Types.DeepType;

public class MarketDataSizeTest {

	@Test
	public void nasdaqTopSizeUsesSizeMultiplier() {
		TopMktDataHandler h = new TopMktDataHandler(contract("NASDAQ", "STK", "AAPL", "USD", null), "gw", 1L, false, false);
		h.tickSize(TickType.BID_SIZE, Decimal.get(5L));
		Assert.assertEquals(5.0, h.topBids[0].getDoubleValue("s"), 0.0);
		TopMktDataHandler scaled = new TopMktDataHandler(contract("NASDAQ", "STK", "AAPL", "USD", null), "gw", 2L, false, false);
		scaled.tickSize(TickType.BID_SIZE, Decimal.get(5L));
		Assert.assertEquals(10.0, scaled.topBids[0].getDoubleValue("s"), 0.0);
		TopMktDataHandler frac = new TopMktDataHandler(contract("NASDAQ", "STK", "AAPL", "USD", null), "gw", 0.01, false, false);
		frac.tickSize(TickType.BID_SIZE, Decimal.get(5L));
		Assert.assertEquals(0.05, frac.topBids[0].getDoubleValue("s"), 1e-9);
	}

	@Test
	public void cashTopSizeIsNotZeroWhenSizeMultiplierIsZero() {
		TopMktDataHandler h = new TopMktDataHandler(contract("IDEALPRO", "CASH", "USD", "HKD", null), "gw", 0L, false, false);
		h.tickSize(TickType.BID_SIZE, Decimal.get(10L));
		Assert.assertEquals(10.0, h.topBids[0].getDoubleValue("s"), 0.0);
	}

	@Test
	public void sehkTopSizeStaysIbLots() {
		TopMktDataHandler h = new TopMktDataHandler(contract("SEHK", "STK", "5", "HKD", "5"), "gw", 100L, false, false);
		h.tickSize(TickType.BID_SIZE, Decimal.get(20L));
		Assert.assertEquals(20.0, h.topBids[0].getDoubleValue("s"), 0.0);
	}

	@Test
	public void nasdaqDepthSizeUsesSizeMultiplier() {
		DeepMktDataHandler h = new DeepMktDataHandler(contract("NASDAQ", "STK", "AAPL", "USD", null), "gw", 1L, false);
		h.updateMktDepth(0, "MM", DeepType.INSERT, DeepSide.BUY, 10.0, Decimal.get(5L));
		Assert.assertEquals(5.0, h.bids.get(0).getDoubleValue("s"), 0.0);
	}

	@Test
	public void sehkDepthSizeStaysIbLots() {
		DeepMktDataHandler h = new DeepMktDataHandler(contract("SEHK", "STK", "5", "HKD", "5"), "gw", 100L, false);
		h.updateMktDepth(0, "MM", DeepType.INSERT, DeepSide.BUY, 50.0, Decimal.get(20L));
		Assert.assertEquals(20.0, h.bids.get(0).getDoubleValue("s"), 0.0);
	}

	@Test
	public void futuresTopSizeUsesContractMultiplierAndSizeMultiplier() {
		IBContract c = contract("CME", "FUT", "ES", "USD", "ES");
		c.multiplier("50");
		TopMktDataHandler h = new TopMktDataHandler(c, "gw", 1L, false, false);
		h.tickSize(TickType.BID_SIZE, Decimal.get(2L));
		Assert.assertEquals(100.0, h.topBids[0].getDoubleValue("s"), 0.0);
	}

	private static IBContract contract(String exchange, String secType, String symbol, String currency, String tradingClass) {
		IBContract c = new IBContract();
		c.exchange(exchange);
		c.secType(secType);
		c.symbol(symbol);
		c.currency(currency);
		if (tradingClass != null)
			c.tradingClass(tradingClass);
		return c;
	}
}
