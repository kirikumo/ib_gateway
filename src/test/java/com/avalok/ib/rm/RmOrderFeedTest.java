package com.avalok.ib.rm;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.ib.client.Contract;
import com.ib.client.Decimal;
import com.ib.client.Order;

public class RmOrderFeedTest {

	@Test
	public void liveWriteOrderAndLateCommission() {
		MemoryRmStore store = new MemoryRmStore();
		RmOrderFeed feed = new RmOrderFeed(store, () -> 1000L);
		Order order = order(31, "uranus_live", "LMT");
		order.totalQuantity(Decimal.get(10));
		Contract contract = stk();

		feed.onLive(contract, order, "Submitted", Decimal.ZERO, Decimal.get(10), 0d);
		feed.onCommission("uranus_live", 2d, "USD");
		feed.onCommission("uranus_live", 9d, "HKD");
		feed.onCommission("uranus_live", 3d, "USD");
		feed.onLive(contract, order, "PreCancelled", Decimal.ZERO, Decimal.get(10), 0d);
		int beforeWarn = store.snapshot().size();
		feed.onLive(contract, order, "WarnState", Decimal.ZERO, Decimal.get(10), 0d);
		Assert.assertEquals(beforeWarn, store.snapshot().size());
		feed.onLive(contract, order, "Filled", Decimal.get(10), Decimal.ZERO, 12d);
		feed.onCommission("uranus_live", 1d, "USD");

		List<Map<String, Object>> pubs = publishes(store);
		Assert.assertEquals(6, pubs.size());
		long[] times = new long[pubs.size()];
		for (int i = 0; i < pubs.size(); i++) times[i] = lng(pubs.get(i).get("updateTimeMs"));
		Assert.assertArrayEquals(new long[] {1000L, 1001L, 1002L, 1003L, 1004L, 1005L}, times);
		Assert.assertEquals("new", pubs.get(0).get("status"));
		Assert.assertEquals("cancelling", pubs.get(3).get("status"));
		Assert.assertEquals("filled", pubs.get(4).get("status"));
		Assert.assertEquals("filled", pubs.get(5).get("status"));
		Assert.assertEquals(0, num(pubs.get(3).get("qty")).compareTo(new BigDecimal("10")));
		Assert.assertEquals(0, num(pubs.get(4).get("qty")).compareTo(new BigDecimal("10")));
		Assert.assertEquals(0, num(pubs.get(4).get("remainedQty")).compareTo(BigDecimal.ZERO));
		Assert.assertEquals(0, num(pubs.get(2).get("commission")).compareTo(new BigDecimal("5")));
		Assert.assertEquals("USD", pubs.get(2).get("commissionCurrency"));
		Assert.assertEquals(0, num(pubs.get(5).get("commission")).compareTo(new BigDecimal("6")));
		Assert.assertNull(store.peek(RmKeys.open("DU1"), "uranus_live"));

		List<MemoryRmStore.Op> ops = store.snapshot();
		int filledPub = -1;
		int filledDel = -1;
		for (int i = 0; i < ops.size(); i++) {
			MemoryRmStore.Op op = ops.get(i);
			if ("PUBLISH".equals(op.kind) && filledPub < 0 && op.value.contains("\"status\":\"filled\"")) {
				filledPub = i;
				Assert.assertEquals("HDEL", ops.get(i - 1).kind);
				filledDel = i - 1;
			}
			if (("new".equals(statusOf(op)) || "cancelling".equals(statusOf(op)))) {
				Assert.assertEquals("HSET", ops.get(i - 1).kind);
				Assert.assertEquals(ops.get(i).value, ops.get(i - 1).value);
			}
		}
		Assert.assertTrue(filledDel >= 0 && filledDel < filledPub);
		for (int i = filledPub + 1; i < ops.size(); i++) {
			Assert.assertFalse("HSET".equals(ops.get(i).kind));
			Assert.assertFalse("HDEL".equals(ops.get(i).kind));
		}
	}

	@Test
	public void cancelledAndRejectedDeleteBeforePublish() {
		assertTerminal("Cancelled", "cancelled", Decimal.get(4), Decimal.get(6));
		assertTerminal("Rejected", "rejected", Decimal.ZERO, Decimal.get(10));
	}

	@Test
	public void permIdZeroWritesNothing() {
		MemoryRmStore store = new MemoryRmStore();
		RmOrderFeed feed = new RmOrderFeed(store, () -> 1000L);
		Order order = order(0, "uranus_skip", "LMT");
		feed.onLive(stk(), order, "Submitted", Decimal.ZERO, Decimal.get(1), 0d);
		Assert.assertTrue(store.snapshot().isEmpty());
	}

	@Test
	public void oneEndAloneDoesNotRemove() {
		MemoryRmStore store = seedRemoved("tws:9");
		RmOrderFeed openFirst = feed(store);
		openFirst.onOpenEnded();
		Assert.assertFalse(joined(store).contains("removed"));
		openFirst.onCompletedEnded();
		Assert.assertTrue(joined(store).contains("\"status\":\"removed\""));

		MemoryRmStore other = seedRemoved("tws:8");
		RmOrderFeed completedFirst = feed(other);
		completedFirst.onCompletedEnded();
		Assert.assertFalse(joined(other).contains("removed"));
		completedFirst.onOpenEnded();
		Assert.assertTrue(joined(other).contains("\"status\":\"removed\""));
	}

	@Test
	public void reconcileFourOutcomesAndSkipsGhost() {
		MemoryRmStore store = new MemoryRmStore();
		seed(store, "uranus_filled", "1", "9", null);
		seed(store, "uranus_filled_noexec", "1", "9", null);
		seed(store, "uranus_cancel", "5", "5", null);
		seed(store, "uranus_reject", "3", "7", null);
		seed(store, "uranus_removed", "1", "4", null);
		seed(store, "uranus_exec", "1", "9", null);
		store.clearOps();

		RmOrderFeed feed = new RmOrderFeed(store, () -> 100L);
		feed.setAccounts(() -> List.of("DU1"));
		feed.beginReconnect();
		Order live = order(70, "uranus_live", "LMT");
		feed.onLive(stk(), live, "Submitted", Decimal.ZERO, Decimal.get(10), 0d);
		feed.onCompleted(stk(), completed(71, "uranus_filled", "2.5"), "Filled");
		feed.onExecution("uranus_filled", Decimal.get(new BigDecimal("2.5")), 10.5d);
		feed.onCompleted(stk(), completed(72, "uranus_filled_noexec", "4"), "Filled");
		feed.onCompleted(stk(), completed(73, "uranus_cancel", "0"), "Cancelled");
		Order rejected = completed(74, "uranus_reject", null);
		rejected.filledQuantity(Decimal.INVALID);
		feed.onCompleted(stk(), rejected, "Rejected");
		feed.onExecution("uranus_exec", Decimal.get(new BigDecimal("1.5")), 7d);
		feed.onCompleted(stk(), completed(75, "uranus_ghost", "1"), "Filled");

		feed.onOpenEnded();
		Assert.assertFalse(joined(store).contains("\"status\":\"removed\""));
		feed.onCompletedEnded();

		Map<String, Map<String, Object>> byRef = new LinkedHashMap<>();
		List<MemoryRmStore.Op> ops = store.snapshot();
		for (int i = 0; i < ops.size(); i++) {
			MemoryRmStore.Op op = ops.get(i);
			if (!"PUBLISH".equals(op.kind)) continue;
			Map<String, Object> doc = RmJson.parse(op.value);
			String ref = String.valueOf(doc.get("orderRef"));
			if ("uranus_live".equals(ref)) continue;
			byRef.put(ref, doc);
			Assert.assertEquals("HDEL", ops.get(i + 1).kind);
			Assert.assertEquals(ref, ops.get(i + 1).field);
			Assert.assertEquals(101L, lng(doc.get("updateTimeMs")));
		}
		Assert.assertFalse(byRef.containsKey("uranus_ghost"));
		Assert.assertFalse(joined(store).contains("uranus_ghost"));

		Map<String, Object> filled = byRef.get("uranus_filled");
		Assert.assertEquals("filled", filled.get("status"));
		Assert.assertEquals(0, num(filled.get("filledQty")).compareTo(new BigDecimal("2.5")));
		Assert.assertEquals(0, num(filled.get("avgPrice")).compareTo(new BigDecimal("10.5")));
		Assert.assertEquals(0, num(filled.get("remainedQty")).compareTo(BigDecimal.ZERO));

		Map<String, Object> noExec = byRef.get("uranus_filled_noexec");
		Assert.assertEquals("filled", noExec.get("status"));
		Assert.assertTrue(noExec.containsKey("avgPrice"));
		Assert.assertNull(noExec.get("avgPrice"));
		Assert.assertEquals(0, num(noExec.get("remainedQty")).compareTo(BigDecimal.ZERO));
		Assert.assertEquals(0, num(noExec.get("filledQty")).compareTo(new BigDecimal("4")));

		Map<String, Object> cancelled = byRef.get("uranus_cancel");
		Assert.assertEquals("cancelled", cancelled.get("status"));
		Assert.assertEquals(0, num(cancelled.get("filledQty")).compareTo(BigDecimal.ZERO));
		Assert.assertEquals(0, num(cancelled.get("remainedQty")).compareTo(BigDecimal.ZERO));

		Map<String, Object> rej = byRef.get("uranus_reject");
		Assert.assertEquals("rejected", rej.get("status"));
		Assert.assertEquals(0, num(rej.get("filledQty")).compareTo(new BigDecimal("3")));
		Assert.assertEquals(0, num(rej.get("remainedQty")).compareTo(BigDecimal.ZERO));

		Map<String, Object> execOnly = byRef.get("uranus_exec");
		Assert.assertEquals("filled", execOnly.get("status"));
		Assert.assertEquals(0, num(execOnly.get("filledQty")).compareTo(new BigDecimal("1.5")));
		Assert.assertEquals(0, num(execOnly.get("avgPrice")).compareTo(new BigDecimal("7")));

		Map<String, Object> removed = byRef.get("uranus_removed");
		Assert.assertEquals("removed", removed.get("status"));
		Assert.assertTrue(removed.containsKey("price"));
		Assert.assertNull(removed.get("price"));
		Assert.assertEquals(0, num(removed.get("filledQty")).compareTo(new BigDecimal("1")));
		Assert.assertEquals(0, num(removed.get("remainedQty")).compareTo(new BigDecimal("4")));

		Assert.assertNotNull(store.peek(RmKeys.open("DU1"), "uranus_live"));
		Assert.assertNull(store.peek(RmKeys.open("DU1"), "uranus_filled"));
		Assert.assertNull(store.peek(RmKeys.open("DU1"), "uranus_cancel"));
		Assert.assertNull(store.peek(RmKeys.open("DU1"), "uranus_reject"));
		Assert.assertNull(store.peek(RmKeys.open("DU1"), "uranus_removed"));
		Assert.assertNull(store.peek(RmKeys.open("DU1"), "uranus_exec"));
	}

	@Test
	public void contractDetailsRepublishSameOrder() {
		MemoryRmStore store = new MemoryRmStore();
		RmOrderFeed feed = new RmOrderFeed(store, () -> 5000L);
		Contract fut = new Contract();
		fut.conid(77);
		fut.secType("FUT");
		fut.symbol("HSI");
		fut.currency("HKD");
		fut.exchange("HKFE");
		fut.multiplier("1");
		Order order = order(77, "uranus_fut", "LMT");
		feed.onLive(fut, order, "Submitted", Decimal.ZERO, Decimal.get(1), 0d);

		List<Map<String, Object>> first = publishes(store);
		Assert.assertEquals(1, first.size());
		Assert.assertNull(first.get(0).get("pair"));
		Assert.assertEquals("uranus_fut", first.get(0).get("orderRef"));

		Contract details = new Contract();
		details.conid(77);
		details.secType("FUT");
		details.symbol("HSI");
		details.currency("HKD");
		details.exchange("HKFE");
		details.multiplier("1");
		details.lastTradeDate("20260728");
		feed.onContractUpdated(details);

		Contract other = new Contract();
		other.conid(88);
		other.secType("FUT");
		other.symbol("HSI");
		other.currency("HKD");
		other.multiplier("1");
		other.lastTradeDate("20260728");
		feed.onContractUpdated(other);

		List<Map<String, Object>> pubs = publishes(store);
		Assert.assertEquals(2, pubs.size());
		Assert.assertEquals("uranus_fut", pubs.get(1).get("orderRef"));
		Assert.assertTrue(String.valueOf(pubs.get(1).get("pair")).contains("@1"));
		Assert.assertTrue(lng(pubs.get(1).get("updateTimeMs")) > lng(pubs.get(0).get("updateTimeMs")));
	}

	@Test
	public void officialIbSourcesUnchanged() throws Exception {
		Process process = new ProcessBuilder("git", "diff", "--name-only", "--", "src/main/java/com/ib")
				.redirectErrorStream(true)
				.start();
		String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		Assert.assertEquals(0, process.waitFor());
		Assert.assertEquals("", out.trim());
		String api = Files.readString(Path.of("src/main/java/com/ib/controller/ApiController.java"));
		Assert.assertTrue(api.contains("reqCompletedOrders(false)"));
		String handler = Files.readString(Path.of("src/main/java/com/avalok/ib/handler/AllOrderHandler.java"));
		Assert.assertTrue(handler.contains("if (!_omsInit) initOMS();"));
		Assert.assertTrue(handler.contains("_deadOrderInit) initOMS();"));
	}

	private static void assertTerminal(String ibStatus, String rmStatus, Decimal filled, Decimal remaining) {
		MemoryRmStore store = new MemoryRmStore();
		RmOrderFeed feed = new RmOrderFeed(store, () -> 1000L);
		Order order = order(40, "uranus_" + rmStatus, "LMT");
		order.totalQuantity(Decimal.get(10));
		if ("rejected".equals(rmStatus)) {
			feed.onLive(stk(), order, ibStatus, filled, remaining, 0d);
		} else {
			feed.onLive(stk(), order, "Submitted", Decimal.ZERO, Decimal.get(10), 0d);
			feed.onLive(stk(), order, ibStatus, filled, remaining, 0d);
		}
		List<MemoryRmStore.Op> ops = store.snapshot();
		int del = -1;
		int pub = -1;
		for (int i = 0; i < ops.size(); i++) {
			if ("HDEL".equals(ops.get(i).kind)) del = i;
			if ("PUBLISH".equals(ops.get(i).kind) && ops.get(i).value.contains("\"status\":\"" + rmStatus + "\""))
				pub = i;
		}
		Assert.assertTrue(del >= 0 && del < pub);
		Map<String, Object> doc = RmJson.parse(ops.get(pub).value);
		Assert.assertEquals(0, num(doc.get("remainedQty")).compareTo(BigDecimal.ZERO));
		Assert.assertEquals(0, num(doc.get("qty")).compareTo(new BigDecimal("10")));
		Assert.assertNull(store.peek(RmKeys.open("DU1"), "uranus_" + rmStatus));
	}

	private static MemoryRmStore seedRemoved(String ref) {
		MemoryRmStore store = new MemoryRmStore();
		seed(store, ref, "1", "1", null);
		store.clearOps();
		return store;
	}

	private static RmOrderFeed feed(MemoryRmStore store) {
		RmOrderFeed feed = new RmOrderFeed(store, () -> 100L);
		feed.setAccounts(() -> List.of("DU1"));
		feed.beginReconnect();
		return feed;
	}

	private static void seed(MemoryRmStore store, String ref, String filled, String remained, Object price) {
		Map<String, Object> doc = new LinkedHashMap<>();
		doc.put("orderRef", ref);
		doc.put("account", "DU1");
		doc.put("status", "new");
		doc.put("price", price);
		doc.put("qty", new BigDecimal("10"));
		doc.put("filledQty", new BigDecimal(filled));
		doc.put("remainedQty", new BigDecimal(remained));
		doc.put("updateTimeMs", 100);
		doc.put("avgPrice", 0);
		store.hset(RmKeys.open("DU1"), ref, RmJson.toJson(doc));
	}

	private static Order completed(long permId, String ref, String filled) {
		Order order = order(permId, ref, "LMT");
		if (filled == null) order.filledQuantity(Decimal.INVALID);
		else order.filledQuantity(Decimal.get(new BigDecimal(filled)));
		return order;
	}

	private static Order order(long permId, String ref, String type) {
		Order order = new Order();
		order.permId(permId);
		order.orderRef(ref);
		order.account("DU1");
		order.orderType(type);
		order.action("BUY");
		order.totalQuantity(Decimal.get(1));
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

	private static List<Map<String, Object>> publishes(MemoryRmStore store) {
		List<Map<String, Object>> out = new ArrayList<>();
		for (MemoryRmStore.Op op : store.snapshot()) {
			if ("PUBLISH".equals(op.kind)) out.add(RmJson.parse(op.value));
		}
		return out;
	}

	private static String joined(MemoryRmStore store) {
		StringBuilder b = new StringBuilder();
		for (MemoryRmStore.Op op : store.snapshot()) {
			if (op.value != null) b.append(op.value);
		}
		return b.toString();
	}

	private static String statusOf(MemoryRmStore.Op op) {
		if (!"PUBLISH".equals(op.kind) || op.value == null) return null;
		Object status = RmJson.parse(op.value).get("status");
		return status == null ? null : status.toString();
	}

	private static long lng(Object v) {
		return ((Number) v).longValue();
	}

	private static BigDecimal num(Object v) {
		if (v instanceof BigDecimal) return (BigDecimal) v;
		return new BigDecimal(v.toString());
	}
}
