package com.avalok.ib.handler;

import java.math.BigDecimal;

import org.junit.Assert;
import org.junit.Test;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.ib.client.Contract;
import com.ib.client.ContractDetails;
import com.ib.client.Decimal;

public class ContractDetailsHandlerSizeTest {

	@Test
	public void fractionalSizeIsNotTruncated() {
		Object v = ContractDetailsHandler.jsonSize(Decimal.parse("0.01"));
		Assert.assertEquals(new BigDecimal("0.01"), v);
		Assert.assertEquals("{\"minSize\":0.01}", jsonOf("minSize", v));
	}

	@Test
	public void integerSizeRemainsOne() {
		Object v = ContractDetailsHandler.jsonSize(Decimal.get(1L));
		Assert.assertEquals(0, new BigDecimal("1").compareTo((BigDecimal) v));
		Assert.assertEquals("{\"minSize\":1}", jsonOf("minSize", v));
	}

	@Test
	public void validZeroIsNumberZeroNotNull() {
		Object v = ContractDetailsHandler.jsonSize(Decimal.ZERO);
		Assert.assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) v));
		Assert.assertEquals("{\"minSize\":0}", jsonOf("minSize", v));
	}

	@Test
	public void nullAndInvalidAreJsonNull() {
		Assert.assertNull(ContractDetailsHandler.jsonSize(null));
		Assert.assertNull(ContractDetailsHandler.jsonSize(Decimal.INVALID));
		Assert.assertNull(ContractDetailsHandler.jsonSize(Decimal.NaN));
		JSONObject j = new JSONObject();
		j.put("minSize", ContractDetailsHandler.jsonSize(null));
		j.put("sizeIncrement", ContractDetailsHandler.jsonSize(Decimal.INVALID));
		Assert.assertNull(j.get("minSize"));
		Assert.assertNull(j.get("sizeIncrement"));
		Assert.assertFalse(JSON.toJSONString(j).contains("9223372036854775807"));
	}

	@Test
	public void detailJsonKeepsFractionalSizeFields() {
		ContractDetails detail = sampleDetail();
		detail.minSize(Decimal.parse("0.01"));
		detail.sizeIncrement(Decimal.get(1L));
		detail.suggestedSizeIncrement(Decimal.ZERO);
		JSONObject j = ContractDetailsHandler.instance.detailToJSONObject(detail);
		Assert.assertEquals(0, new BigDecimal("0.01").compareTo(j.getBigDecimal("minSize")));
		Assert.assertEquals(0, new BigDecimal("1").compareTo(j.getBigDecimal("sizeIncrement")));
		Assert.assertEquals(0, BigDecimal.ZERO.compareTo(j.getBigDecimal("suggestedSizeIncrement")));
	}

	@Test
	public void unsetSizeFieldsAreNull() {
		ContractDetails detail = sampleDetail();
		JSONObject j = ContractDetailsHandler.instance.detailToJSONObject(detail);
		Assert.assertNull(j.get("minSize"));
		Assert.assertNull(j.get("sizeIncrement"));
		Assert.assertNull(j.get("suggestedSizeIncrement"));
	}

	private static String jsonOf(String key, Object v) {
		JSONObject j = new JSONObject();
		j.put(key, v);
		return JSON.toJSONString(j);
	}

	private static ContractDetails sampleDetail() {
		Contract c = new Contract();
		c.symbol("USD");
		c.secType("CASH");
		c.exchange("IDEALPRO");
		c.currency("HKD");
		ContractDetails d = new ContractDetails();
		d.contract(c);
		return d;
	}
}
