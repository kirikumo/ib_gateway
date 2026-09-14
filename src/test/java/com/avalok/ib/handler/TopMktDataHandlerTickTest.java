package com.avalok.ib.handler;

import org.junit.Assert;
import org.junit.Test;

import com.avalok.ib.IBContract;
import com.ib.client.TickAttrib;
import com.ib.client.TickType;

public class TopMktDataHandlerTickTest {
	private TopMktDataHandler handler() {
		IBContract c = new IBContract() {
			@Override
			public String shownName() {
				return "SEHK:STK:HKD-7200";
			}
			@Override
			public String pair() {
				return "HKD-7200";
			}
		};
		c.exchange("SEHK");
		return new TopMktDataHandler(c, "test_gw", 1L, false, false);
	}

	@Test
	public void delayedBidUpdatesSameFieldAsLiveBid() {
		TopMktDataHandler h = handler();
		TickAttrib attribs = new TickAttrib();
		h.tickPrice(TickType.BID, 10.0, attribs);
		Assert.assertEquals(10.0, h.topBids[0].getDoubleValue("p"), 0.0);
		h.tickPrice(TickType.DELAYED_BID, 11.5, attribs);
		Assert.assertEquals(11.5, h.topBids[0].getDoubleValue("p"), 0.0);
	}

	@Test
	public void unknownTickTypeDoesNotThrow() {
		handler().tickPrice(TickType.UNKNOWN, 1.0, new TickAttrib());
	}
}
