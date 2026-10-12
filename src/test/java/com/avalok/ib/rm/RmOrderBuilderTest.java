package com.avalok.ib.rm;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.ib.client.ComboLeg;
import com.ib.client.Contract;
import com.ib.client.Decimal;
import com.ib.client.Order;

public class RmOrderBuilderTest {

	@Test
	public void trailTrailLimitStpBagFuturesAndPermIdZero() throws Exception {
		Order trail = order(11, "uranus_trail", "TRAIL");
		trail.lmtPrice(10);
		trail.auxPrice(2);
		Map<String, Object> trailDoc = RmOrderBuilder.build(
				stk(), trail, "Submitted", Decimal.ZERO, Decimal.get(10), 0d, true, null, null, null);
		Assert.assertNull(trailDoc.get("price"));
		Assert.assertEquals("TRAIL", trailDoc.get("orderType"));

		Order trailLimit = order(12, "uranus_traillmt", "TRAIL LIMIT");
		trailLimit.lmtPrice(10);
		trailLimit.auxPrice(2);
		Map<String, Object> trailLimitDoc = RmOrderBuilder.build(
				stk(), trailLimit, "Submitted", Decimal.ZERO, Decimal.get(10), 0d, true, null, null, null);
		Assert.assertNull(trailLimitDoc.get("price"));
		Assert.assertEquals("TRAIL LIMIT", trailLimitDoc.get("orderType"));

		Order stp = order(13, "uranus_stp", "STP");
		stp.auxPrice(12.5);
		stp.lmtPrice(99);
		Map<String, Object> stpDoc = RmOrderBuilder.build(
				stk(), stp, "Submitted", Decimal.ZERO, Decimal.get(10), 0d, true, null, null, null);
		Assert.assertEquals(12.5, ((Number) stpDoc.get("price")).doubleValue(), 0d);

		Order bagOrder = order(14, "uranus_bag", "LMT");
		bagOrder.lmtPrice(-1.25);
		bagOrder.action("BUY");
		Contract bag = new Contract();
		bag.secType("BAG");
		bag.currency("USD");
		bag.symbol("BAG");
		bag.comboLegs().add(new ComboLeg(123, 2, "SSHORT", "SMART", 0));
		Map<String, Object> bagDoc = RmOrderBuilder.build(
				bag, bagOrder, "Submitted", Decimal.ZERO, Decimal.get(10), 0d, true, null, null, null);
		Assert.assertEquals(-1.25, ((Number) bagDoc.get("price")).doubleValue(), 0d);
		Assert.assertNotNull(bagDoc.get("comboLegs"));
		@SuppressWarnings("unchecked")
		Map<String, Object> leg = (Map<String, Object>) ((java.util.List<?>) bagDoc.get("comboLegs")).get(0);
		Assert.assertNull(leg.get("price"));
		Assert.assertNull(leg.get("exchange"));
		Assert.assertEquals("SELL", leg.get("side"));
		Assert.assertEquals(0, num(leg.get("qty")).compareTo(new BigDecimal("20")));

		Contract undated = fut("");
		undated.lastTradeDateOrContractMonth("202607");
		Map<String, Object> undatedDoc = RmOrderBuilder.build(
				undated, order(15, "uranus_fut", "LMT"), "Submitted",
				Decimal.ZERO, Decimal.get(1), 0d, true, null, null, null);
		Assert.assertNotNull(undatedDoc);
		Assert.assertNull(undatedDoc.get("pair"));

		Contract dated = fut("20260728");
		dated.multiplier("1");
		Map<String, Object> datedDoc = RmOrderBuilder.build(
				dated, order(16, "uranus_fut2", "LMT"), "Submitted",
				Decimal.ZERO, Decimal.get(1), 0d, true, null, null, null);
		Assert.assertEquals("HKD-HSI@20260728@1", datedDoc.get("pair"));

		Order zeroPerm = order(0, "uranus_skip", "LMT");
		Assert.assertNull(RmOrderBuilder.orderRef(zeroPerm));
		Assert.assertNull(RmOrderBuilder.build(
				stk(), zeroPerm, "Submitted", Decimal.ZERO, Decimal.get(1), 0d, true, null, null, null));

		Order zeroPrice = order(17, "uranus_px", "LMT");
		zeroPrice.lmtPrice(0d);
		Assert.assertNull(RmOrderBuilder.build(
				stk(), zeroPrice, "Submitted", Decimal.ZERO, Decimal.get(1), 0d, true, null, null, null).get("price"));
		zeroPrice.lmtPrice(Double.MAX_VALUE);
		Assert.assertNull(RmOrderBuilder.build(
				stk(), zeroPrice, "Submitted", Decimal.ZERO, Decimal.get(1), 0d, true, null, null, null).get("price"));

		Order mit = order(18, "uranus_mit", "MIT");
		mit.lmtPrice(15);
		Assert.assertNull(RmOrderBuilder.build(
				stk(), mit, "Submitted", Decimal.ZERO, Decimal.get(1), 0d, true, null, null, null).get("price"));

		Order fractional = order(19, "uranus_qty", "MKT");
		fractional.totalQuantity(Decimal.get(new BigDecimal("1.5")));
		Map<String, Object> qtyDoc = RmOrderBuilder.build(
				stk(), fractional, "Submitted", Decimal.ZERO, Decimal.ZERO, 0d, true, null, null, null);
		Assert.assertEquals(0, num(qtyDoc.get("qty")).compareTo(new BigDecimal("1.5")));

		Assert.assertEquals("cancelling", RmOrderBuilder.mapStatus("PreCancelled"));
		Map<String, Object> pre = RmOrderBuilder.build(
				stk(), order(20, "uranus_pre", "LMT"), "PreCancelled",
				Decimal.ZERO, Decimal.get(1), 0d, true, null, null, null);
		Assert.assertEquals("cancelling", pre.get("status"));

		Order sshort = order(21, "uranus_ss", "LMT");
		sshort.action("SSHORT");
		Assert.assertEquals("SELL", RmOrderBuilder.build(
				stk(), sshort, "Submitted", Decimal.ZERO, Decimal.get(1), 0d, true, null, null, null).get("side"));

		Assert.assertEquals("uranus_keep", RmOrderBuilder.orderRef(order(22, "uranus_keep", "LMT")));
		Assert.assertEquals("api_keep", RmOrderBuilder.orderRef(order(23, "api_keep", "LMT")));
		Order manual = order(24, "", "LMT");
		Assert.assertEquals("tws:24", RmOrderBuilder.orderRef(manual));
		Assert.assertNull(RmOrderBuilder.mapStatus("WarnState"));

		String src = Files.readString(Path.of("src/main/java/com/avalok/ib/rm/RmOrderBuilder.java"));
		Assert.assertFalse(src.contains("toOMSJSON"));
		Assert.assertFalse(src.contains("isAlive"));
		Assert.assertFalse(src.contains("IBContract"));
	}

	private static BigDecimal num(Object v) {
		if (v instanceof BigDecimal) return (BigDecimal) v;
		return new BigDecimal(v.toString());
	}

	private static Order order(long permId, String ref, String type) {
		Order order = new Order();
		order.permId(permId);
		order.orderRef(ref);
		order.account("DU1");
		order.orderType(type);
		order.action("BUY");
		order.totalQuantity(Decimal.get(10));
		return order;
	}

	private static Contract stk() {
		Contract c = new Contract();
		c.secType("STK");
		c.symbol("AAPL");
		c.currency("USD");
		c.exchange("SMART");
		c.primaryExch("NASDAQ");
		return c;
	}

	private static Contract fut(String lastTradeDate) {
		Contract c = new Contract();
		c.secType("FUT");
		c.symbol("HSI");
		c.currency("HKD");
		c.exchange("HKFE");
		c.multiplier("1");
		c.conid(77);
		if (lastTradeDate != null && !lastTradeDate.isEmpty()) c.lastTradeDate(lastTradeDate);
		return c;
	}
}
