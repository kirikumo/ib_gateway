package com.avalok.ib.rm;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import com.ib.client.ComboLeg;
import com.ib.client.Contract;
import com.ib.client.Decimal;
import com.ib.client.Order;
import com.ib.client.Types.Right;

/**
 * While connected: open orders HSET then PUBLISH; terminal orders HDEL then PUBLISH.
 * Reconnect waits for both openOrderEnd and completedOrdersEnd, then PUBLISH then HDEL.
 */
public final class RmOrderFeed {
	private final RmStore store;
	private final RmClock clock;
	private final LongSupplier nowMs;
	private Supplier<List<String>> accounts = List::of;

	private final Map<String, Live> lives = new ConcurrentHashMap<>();
	private final Map<Integer, Contract> detailByConId = new ConcurrentHashMap<>();
	private final Set<String> snapshotRefs = ConcurrentHashMap.newKeySet();
	private final Map<String, Completed> completed = new ConcurrentHashMap<>();
	private final Map<String, Exec> execs = new ConcurrentHashMap<>();
	private final Set<String> seenAccounts = ConcurrentHashMap.newKeySet();

	private boolean awaitingReconcile;
	private boolean openEnded;
	private boolean completedEnded;
	private boolean reconciled;

	public RmOrderFeed(RmStore store) {
		this(store, System::currentTimeMillis);
	}

	/** Test only. Supplies the clock. Production uses {@link #RmOrderFeed(RmStore)}. */
	public RmOrderFeed(RmStore store, LongSupplier nowMs) {
		this.store = store;
		this.clock = new RmClock();
		this.nowMs = nowMs;
	}

	public void setAccounts(Supplier<List<String>> accounts) {
		if (accounts != null) this.accounts = accounts;
	}

	public synchronized void beginReconnect() {
		awaitingReconcile = true;
		openEnded = false;
		completedEnded = false;
		reconciled = false;
		snapshotRefs.clear();
		completed.clear();
		execs.clear();
		seenAccounts.clear();
	}

	public synchronized void noteSnapshot(Contract contract, Order order) {
		String ref = RmOrderBuilder.orderRef(order);
		if (ref == null) return;
		if (awaitingReconcile) snapshotRefs.add(ref);
		if (order.account() != null && !order.account().isEmpty()) seenAccounts.add(order.account());
	}

	public synchronized void onLive(
			Contract contract, Order order, String ibStatus,
			Decimal filled, Decimal remaining, double avg) {
		noteSnapshot(contract, order);
		if ("WarnState".equals(ibStatus)) return;
		String ref = RmOrderBuilder.orderRef(order);
		if (ref == null || RmOrderBuilder.mapStatus(ibStatus) == null) return;
		Live live = lives.computeIfAbsent(ref, k -> new Live());
		live.contract = refresh(live.contract, contract);
		live.order = order;
		live.ibStatus = ibStatus;
		live.filled = filled;
		live.remaining = remaining;
		live.avg = avg;
		live.zeroAvgWhenNoFill = filled == null || !filled.isValid() || filled.isZero();
		write(live);
	}

	public synchronized void onCompleted(Contract contract, Order order, String ibStatus) {
		String ref = RmOrderBuilder.orderRef(order);
		if (ref == null) return;
		if (order.account() != null && !order.account().isEmpty()) seenAccounts.add(order.account());
		completed.put(ref, new Completed(order, ibStatus));
	}

	public synchronized void onExecution(String orderRef, Decimal cumQty, double avgPrice) {
		if (orderRef == null || orderRef.isEmpty()) return;
		execs.put(orderRef, new Exec(cumQty, avgPrice));
	}

	public synchronized void onCommission(String orderRef, double amount, String reportCurrency) {
		if (orderRef == null || orderRef.isEmpty()) return;
		if (!Double.isFinite(amount) || amount == Double.MAX_VALUE) return;
		Live live = lives.get(orderRef);
		if (live == null) return;
		String fallback = live.contract == null ? null : live.contract.currency();
		String cur = reportCurrency == null || reportCurrency.isEmpty() ? fallback : reportCurrency;
		if (!live.currencyLocked) {
			live.currencyLocked = true;
			live.commissionCurrency = cur;
			live.commission = BigDecimal.valueOf(amount);
		} else if (cur != null && cur.equals(live.commissionCurrency)) {
			live.commission = live.commission.add(BigDecimal.valueOf(amount));
		}
		write(live);
	}

	public synchronized void onContractUpdated(Contract details) {
		if (details == null || details.conid() == 0) return;
		Contract copy = snapshot(details);
		detailByConId.put(copy.conid(), copy);
		for (Live live : new ArrayList<>(lives.values())) {
			if (live.contract != null && live.contract.conid() == copy.conid())
				applyDetails(live.contract, copy);
			if (references(live, copy.conid())) write(live);
		}
	}

	public synchronized void onOpenEnded() {
		openEnded = true;
		tryReconcile();
	}

	public synchronized void onCompletedEnded() {
		completedEnded = true;
		tryReconcile();
	}

	private void write(Live live) {
		if (live.order == null || live.contract == null || live.ibStatus == null) return;
		Map<String, Object> doc = RmOrderBuilder.build(
				live.contract, live.order, live.ibStatus,
				live.filled, live.remaining, live.avg, live.zeroAvgWhenNoFill,
				live.commission, live.commissionCurrency, detailByConId);
		if (doc == null) return;
		if (live.leftOpen && live.rmStatus != null) {
			doc.put("status", live.rmStatus);
			if ("removed".equals(live.rmStatus)) {
				if (live.keptRemained != null) doc.put("remainedQty", live.keptRemained);
			} else if (RmOrderBuilder.terminal(live.rmStatus)) {
				doc.put("remainedQty", BigDecimal.ZERO);
			}
		}
		String ref = String.valueOf(doc.get("orderRef"));
		String account = doc.get("account") == null ? null : String.valueOf(doc.get("account"));
		if (account == null || account.isEmpty() || "null".equals(account)) return;
		String body = RmJson.toJson(doc);
		if (body.equals(live.lastBody)) return;
		live.lastBody = body;
		String prev = store.hget(RmKeys.open(account), ref);
		long t = clock.next(ref, nowMs.getAsLong(), prev);
		doc.put("updateTimeMs", t);
		String json = RmJson.toJson(doc);
		String status = String.valueOf(doc.get("status"));
		boolean stayOpen = "new".equals(status) || "cancelling".equals(status);
		if (stayOpen) {
			store.hset(RmKeys.open(account), ref, json);
			store.publish(RmKeys.order(account), json);
			live.inOpen = true;
			live.leftOpen = false;
		} else if (live.leftOpen) {
			store.publish(RmKeys.order(account), json);
		} else {
			store.hdel(RmKeys.open(account), ref);
			store.publish(RmKeys.order(account), json);
			live.inOpen = false;
			live.leftOpen = true;
		}
		live.rmStatus = status;
	}

	private void tryReconcile() {
		if (!openEnded || !completedEnded || reconciled) return;
		reconciled = true;
		awaitingReconcile = false;
		LinkedHashSet<String> accs = new LinkedHashSet<>();
		List<String> listed = accounts.get();
		if (listed != null) accs.addAll(listed);
		accs.addAll(seenAccounts);
		long now = nowMs.getAsLong();
		for (String account : accs) {
			if (account == null || account.isEmpty()) continue;
			Map<String, String> hash = store.hgetAll(RmKeys.open(account));
			if (hash == null) continue;
			for (Map.Entry<String, String> e : new ArrayList<>(hash.entrySet())) {
				if (snapshotRefs.contains(e.getKey())) continue;
				publishThenDelete(account, e.getKey(), e.getValue(), now);
			}
		}
	}

	private void publishThenDelete(String account, String ref, String previousJson, long now) {
		Map<String, Object> doc = new LinkedHashMap<>(RmJson.parse(previousJson));
		Completed done = completed.get(ref);
		Exec exec = execs.get(ref);
		String status = reconcileStatus(done, exec);
		doc.put("orderRef", ref);
		doc.put("account", account);
		doc.put("status", status);
		if (!"removed".equals(status)) {
			doc.put("remainedQty", 0);
			BigDecimal filledQty = pickFilled(status, done, exec, doc);
			if (filledQty != null) doc.put("filledQty", filledQty);
			if ("filled".equals(status)) {
				if (exec != null && Double.isFinite(exec.avg) && exec.avg != Double.MAX_VALUE)
					doc.put("avgPrice", exec.avg);
				else
					doc.put("avgPrice", null);
			}
		}
		long t = clock.next(ref, now, previousJson);
		doc.put("updateTimeMs", t);
		String json = RmJson.toJson(doc);
		store.publish(RmKeys.order(account), json);
		store.hdel(RmKeys.open(account), ref);
		Live live = lives.get(ref);
		if (live != null) {
			live.inOpen = false;
			live.leftOpen = true;
			live.rmStatus = status;
			live.keptRemained = doc.get("remainedQty");
			live.lastBody = null;
		}
	}

	private static String reconcileStatus(Completed done, Exec exec) {
		String ib = done == null ? null : done.ibStatus;
		if ("Filled".equals(ib)) return "filled";
		if ("Cancelled".equals(ib) || "Canceled".equals(ib) || "ApiCancelled".equals(ib)) return "cancelled";
		if ("Rejected".equals(ib)) return "rejected";
		if (exec != null) return "filled";
		return "removed";
	}

	private static BigDecimal pickFilled(String status, Completed done, Exec exec, Map<String, Object> prev) {
		BigDecimal fromDone = done == null ? null : decimalOrNull(done.order.filledQuantity());
		if ("filled".equals(status)) {
			if (fromDone != null) return fromDone;
			if (exec != null && exec.cum != null && exec.cum.isValid()) return exec.cum.value();
			return asDecimal(prev.get("filledQty"));
		}
		if (fromDone != null) return fromDone;
		return asDecimal(prev.get("filledQty"));
	}

	private static boolean references(Live live, int conId) {
		if (live.contract != null && live.contract.conid() == conId) return true;
		if (live.contract == null || live.contract.comboLegs() == null) return false;
		for (ComboLeg leg : live.contract.comboLegs()) {
			if (leg.conid() == conId) return true;
		}
		return false;
	}

	private static Contract refresh(Contract dest, Contract src) {
		if (src == null) return dest;
		if (dest == null) return snapshot(src);
		applyDetails(dest, src);
		if (src.comboLegs() != null && !src.comboLegs().isEmpty())
			dest.comboLegs(new ArrayList<>(src.comboLegs()));
		return dest;
	}

	private static void applyDetails(Contract dest, Contract src) {
		if (src.conid() != 0) dest.conid(src.conid());
		if (notEmpty(src.symbol())) dest.symbol(src.symbol());
		if (notEmpty(src.getSecType())) dest.secType(src.getSecType());
		if (notEmpty(src.currency())) dest.currency(src.currency());
		if (notEmpty(src.multiplier())) dest.multiplier(src.multiplier());
		if (RmPair.venue(src.exchange()) != null && RmPair.venue(dest.exchange()) == null)
			dest.exchange(src.exchange());
		else if (notEmpty(src.exchange()) && empty(dest.exchange()))
			dest.exchange(src.exchange());
		if (RmPair.venue(src.primaryExch()) != null) dest.primaryExch(src.primaryExch());
		if (notEmpty(src.tradingClass())) dest.tradingClass(src.tradingClass());
		if (RmPair.ymd(src.lastTradeDate()) != null) dest.lastTradeDate(src.lastTradeDate());
		if (src.strike() != 0d) dest.strike(src.strike());
		if (src.right() != null && src.right() != Right.None) dest.right(src.right());
	}

	private static Contract snapshot(Contract c) {
		Contract n = new Contract();
		applyDetails(n, c);
		if (c.comboLegs() != null) n.comboLegs(new ArrayList<>(c.comboLegs()));
		n.lastTradeDateOrContractMonth(c.lastTradeDateOrContractMonth());
		return n;
	}

	private static BigDecimal decimalOrNull(Decimal d) {
		if (d == null || !d.isValid()) return null;
		return d.value();
	}

	private static BigDecimal asDecimal(Object v) {
		if (v instanceof BigDecimal) return (BigDecimal) v;
		if (v instanceof Number) return BigDecimal.valueOf(((Number) v).doubleValue());
		if (v instanceof String) {
			try { return new BigDecimal((String) v); }
			catch (NumberFormatException e) { return null; }
		}
		return null;
	}

	private static boolean notEmpty(String s) { return s != null && !s.isEmpty(); }
	private static boolean empty(String s) { return !notEmpty(s); }

	private static final class Live {
		Contract contract;
		Order order;
		String ibStatus;
		Decimal filled;
		Decimal remaining;
		double avg;
		boolean zeroAvgWhenNoFill = true;
		BigDecimal commission = BigDecimal.ZERO;
		String commissionCurrency;
		boolean currencyLocked;
		boolean inOpen;
		boolean leftOpen;
		String rmStatus;
		Object keptRemained;
		String lastBody;
	}

	private static final class Completed {
		final Order order;
		final String ibStatus;
		Completed(Order order, String ibStatus) {
			this.order = order;
			this.ibStatus = ibStatus;
		}
	}

	private static final class Exec {
		final Decimal cum;
		final double avg;
		Exec(Decimal cum, double avg) {
			this.cum = cum;
			this.avg = avg;
		}
	}
}
