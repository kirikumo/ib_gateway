package com.avalok.ib.handler;

import java.lang.reflect.Constructor;

import org.junit.Assert;
import org.junit.Test;

import com.alibaba.fastjson.JSONObject;
import com.avalok.ib.IBOrder;
import com.ib.client.Contract;
import com.ib.client.Decimal;
import com.ib.client.Order;
import com.ib.client.OrderState;
import com.ib.client.OrderStatus;

public class IBOrderChildOrderTest {

	private Contract createContract() {
		Contract c = new Contract();
		c.conid(123456);
		c.symbol("MES");
		c.secType("FUT");
		c.currency("USD");
		c.exchange("CME");
		c.lastTradeDateOrContractMonth("20261218");
		c.multiplier("5");
		return c;
	}

	private OrderState createOrderState() {
		try {
			Constructor<OrderState> c = OrderState.class.getDeclaredConstructor();
			c.setAccessible(true);
			return c.newInstance();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	public void testChildOrderRefNormalizationAndIdempotency() {
		Contract contract = createContract();

		Order parentOrder = new Order();
		parentOrder.orderId(1);
		parentOrder.permId(812216930);
		parentOrder.orderRef("api_scale_1792635490740");
		parentOrder.action("BUY");
		parentOrder.totalQuantity(Decimal.get(10));
		parentOrder.lmtPrice(7750.0);

		IBOrder parentIBO = new IBOrder(contract, parentOrder, createOrderState());
		Assert.assertFalse(parentIBO.isChildOrder());
		Assert.assertEquals("api_scale_1792635490740", parentIBO.omsClientOID());
		Assert.assertEquals("api_scale_1792635490740", parentIBO.orderRef());
		Assert.assertEquals("api_scale_1792635490740", parentOrder.orderRef());

		Order childOrder = new Order();
		childOrder.orderId(2);
		childOrder.permId(812216931);
		childOrder.parentId(1);
		childOrder.parentPermId(812216930);
		childOrder.orderRef("api_scale_1792635490740");
		childOrder.action("SELL");
		childOrder.totalQuantity(Decimal.get(10));
		childOrder.lmtPrice(7765.0);

		IBOrder childIBO = new IBOrder(contract, childOrder, createOrderState());
		Assert.assertTrue(childIBO.isChildOrder());
		Assert.assertEquals("api_scale_1792635490740@812216931", childIBO.omsClientOID());
		Assert.assertEquals("api_scale_1792635490740@812216931", childIBO.orderRef());
		Assert.assertEquals("api_scale_1792635490740@812216931", childIBO.omsId());
		Assert.assertEquals("api_scale_1792635490740", childOrder.orderRef());

		String childRef = childIBO.orderRef();
		String extractedParentRef = childRef.substring(0, childRef.lastIndexOf('@'));
		Assert.assertEquals("api_scale_1792635490740", extractedParentRef);

		Assert.assertEquals("api_scale_1792635490740@812216931", childIBO.orderRef());
		Assert.assertEquals("api_scale_1792635490740", childOrder.orderRef());
	}

	@Test
	public void testChildWithoutPermIdDoesNotTakeParentKey() {
		Contract contract = createContract();

		Order parentOrder = new Order();
		parentOrder.orderId(1);
		parentOrder.permId(812216930);
		parentOrder.orderRef("api_scale_1792635490740");
		IBOrder parentIBO = new IBOrder(contract, parentOrder, createOrderState());

		Order childOrder = new Order();
		childOrder.orderId(2);
		childOrder.permId(0);
		childOrder.parentId(1);
		childOrder.parentPermId(812216930);
		childOrder.orderRef("api_scale_1792635490740");
		IBOrder childIBO = new IBOrder(contract, childOrder, createOrderState());

		Assert.assertNull(childIBO.orderRef());
		Assert.assertNull(childIBO.omsId());
		Assert.assertEquals("api_scale_1792635490740", childOrder.orderRef());

		OrderCache cache = new OrderCache();
		cache.recOrder(parentIBO);
		cache.recOrder(childIBO);
		Assert.assertSame(parentIBO, cache.byOMSId("api_scale_1792635490740"));
	}

	@Test
	public void testToOMSJSONParentAndChildFields() {
		Contract contract = createContract();

		Order parentOrder = new Order();
		parentOrder.orderId(1);
		parentOrder.permId(812216930);
		parentOrder.orderRef("api_scale_1792635490740");
		parentOrder.action("BUY");
		parentOrder.totalQuantity(Decimal.get(10));
		parentOrder.lmtPrice(7750.0);

		IBOrder parentIBO = new IBOrder(contract, parentOrder, createOrderState());
		parentIBO.setStatus(OrderStatus.Submitted, Decimal.ZERO, Decimal.get(10), 0d, 812216930, 0, 0d, 0, "", 0d);
		JSONObject parentJson = parentIBO.toOMSJSON();

		Assert.assertEquals("api_scale_1792635490740", parentJson.getString("client_oid"));
		Assert.assertEquals("api_scale_1792635490740", parentJson.getString("orderRef"));
		Assert.assertNull(parentJson.getString("parentOrderRef"));
		Assert.assertNull(parentJson.get("parentId"));
		Assert.assertNull(parentJson.get("parentPermId"));

		Order childOrder = new Order();
		childOrder.orderId(2);
		childOrder.permId(812216931);
		childOrder.parentId(1);
		childOrder.parentPermId(812216930);
		childOrder.orderRef("api_scale_1792635490740");
		childOrder.action("SELL");
		childOrder.totalQuantity(Decimal.get(10));
		childOrder.lmtPrice(7765.0);

		IBOrder childIBO = new IBOrder(contract, childOrder, createOrderState());
		childIBO.setStatus(OrderStatus.Submitted, Decimal.ZERO, Decimal.get(10), 0d, 812216931, 1, 0d, 0, "", 0d);
		JSONObject childJson = childIBO.toOMSJSON();

		Assert.assertEquals("api_scale_1792635490740@812216931", childJson.getString("client_oid"));
		Assert.assertEquals("api_scale_1792635490740@812216931", childJson.getString("orderRef"));
		Assert.assertEquals("api_scale_1792635490740@812216931", childJson.getString("i"));
		Assert.assertEquals("api_scale_1792635490740", childOrder.orderRef());
		Assert.assertNull(childJson.getString("parentOrderRef"));
		Assert.assertEquals(1, childJson.getIntValue("parentId"));
		Assert.assertEquals("812216930", childJson.getString("parentPermId"));
	}

	@Test
	public void testOrderCacheCoexistence() {
		Contract contract = createContract();

		Order parent = new Order();
		parent.orderId(1);
		parent.permId(812216930);
		parent.orderRef("api_scale_1792635490740");
		parent.action("BUY");
		parent.totalQuantity(Decimal.get(10));
		parent.lmtPrice(7750.0);
		IBOrder parentIBO = new IBOrder(contract, parent, createOrderState());
		parentIBO.setStatus(OrderStatus.Filled, Decimal.get(10), Decimal.ZERO, 7750.0, 812216930, 0, 7750.0, 0, "", 0d);

		Order child1 = new Order();
		child1.orderId(2);
		child1.permId(812216931);
		child1.parentId(1);
		child1.parentPermId(812216930);
		child1.orderRef("api_scale_1792635490740");
		child1.action("SELL");
		child1.totalQuantity(Decimal.get(5));
		child1.lmtPrice(7765.0);
		IBOrder child1IBO = new IBOrder(contract, child1, createOrderState());
		child1IBO.setStatus(OrderStatus.Submitted, Decimal.ZERO, Decimal.get(5), 0d, 812216931, 1, 0d, 0, "", 0d);

		Order child2 = new Order();
		child2.orderId(3);
		child2.permId(812216932);
		child2.parentId(1);
		child2.parentPermId(812216930);
		child2.orderRef("api_scale_1792635490740");
		child2.action("SELL");
		child2.totalQuantity(Decimal.get(5));
		child2.lmtPrice(7770.0);
		IBOrder child2IBO = new IBOrder(contract, child2, createOrderState());
		child2IBO.setStatus(OrderStatus.Submitted, Decimal.ZERO, Decimal.get(5), 0d, 812216932, 1, 0d, 0, "", 0d);

		OrderCache cache = new OrderCache();
		cache.recOrder(parentIBO);
		cache.recOrder(child1IBO);
		cache.recOrder(child2IBO);

		// Assert parent and both children coexist in cache without overwriting
		Assert.assertSame(parentIBO, cache.byOMSId("api_scale_1792635490740"));
		Assert.assertSame(child1IBO, cache.byOMSId("api_scale_1792635490740@812216931"));
		Assert.assertSame(child2IBO, cache.byOMSId("api_scale_1792635490740@812216932"));

		Assert.assertSame(parentIBO, cache.byId(1));
		Assert.assertSame(child1IBO, cache.byId(2));
		Assert.assertSame(child2IBO, cache.byId(3));

		Assert.assertSame(parentIBO, cache.byPermId(812216930));
		Assert.assertSame(child1IBO, cache.byPermId(812216931));
		Assert.assertSame(child2IBO, cache.byPermId(812216932));
	}

	@Test
	public void testSetParamsScaleTrader() {
		Contract contract = createContract();

		Order order = new Order();
		order.orderId(10);
		order.permId(608422602);
		order.orderRef("api_scale_7705_restart_1793865189722");
		order.action("SELL");
		order.totalQuantity(Decimal.get(5));
		order.lmtPrice(7705.0);
		order.scaleProfitOffset(6.0);
		order.scalePriceIncrement(5.0);
		order.scaleAutoReset(true);

		IBOrder ibo = new IBOrder(contract, order, createOrderState());
		ibo.setStatus(OrderStatus.Submitted, Decimal.get(4), Decimal.get(1), 7716.75, 608422602, 0, 7720.0, 0, "", 0d);

		JSONObject initialJson = ibo.toOMSJSON();
		Assert.assertEquals(5L, initialJson.getLongValue("ttl_qty"));
		Assert.assertEquals(6.0, initialJson.getDoubleValue("scaleProfitOffset"), 0.0001);
		Assert.assertEquals(1L, initialJson.getLongValue("remained_qty"));

		// 1. Modify PT and increase max position (scaleProfitOffset = 8.0, s = 8)
		JSONObject changeInfo1 = new JSONObject();
		changeInfo1.put("s", 8);
		changeInfo1.put("p", 7710.0);
		changeInfo1.put("scaleProfitOffset", 8.0);
		changeInfo1.put("scaleSubsLevelSize", 2);
		ibo.setParams(changeInfo1);

		Assert.assertEquals(8.0, order.totalQuantity().value().doubleValue(), 0.0001);
		Assert.assertEquals(7710.0, order.lmtPrice(), 0.0001);
		Assert.assertEquals(8.0, order.scaleProfitOffset(), 0.0001);
		Assert.assertEquals(2, order.scaleSubsLevelSize());

		JSONObject modifiedJson1 = ibo.toOMSJSON();
		Assert.assertEquals(8L, modifiedJson1.getLongValue("ttl_qty"));
		Assert.assertEquals(7710.0, modifiedJson1.getDoubleValue("p"), 0.0001);
		Assert.assertEquals(8.0, modifiedJson1.getDoubleValue("scaleProfitOffset"), 0.0001);
		Assert.assertEquals(2, modifiedJson1.getIntValue("scaleSubsLevelSize"));
		Assert.assertEquals(4L, modifiedJson1.getLongValue("remained_qty")); // 8 - 4 executed = 4

		// 2. Soft-lock position test: reduce s to current executed quantity (4)
		JSONObject changeInfo2 = new JSONObject();
		changeInfo2.put("s", 4);
		ibo.setParams(changeInfo2);

		Assert.assertEquals(4.0, order.totalQuantity().value().doubleValue(), 0.0001);
		JSONObject modifiedJson2 = ibo.toOMSJSON();
		Assert.assertEquals(4L, modifiedJson2.getLongValue("ttl_qty"));
		Assert.assertEquals(0L, modifiedJson2.getLongValue("remained_qty")); // 4 - 4 executed = 0, locked!
	}
}
