package com.avalok.ib.rm;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ib.client.ComboLeg;
import com.ib.client.Contract;
import com.ib.client.Decimal;
import com.ib.client.Order;
import com.ib.client.OrderComboLeg;
import com.ib.client.OrderType;
import com.ib.client.Types.Action;

/**
 * Builds a risk order from an IB Contract and Order.
 * Does not reuse OMS JSON, liveness, or the contract pair.
 * Returns null when permId is 0, the status is WarnState, or the status is unmapped.
 */
public final class RmOrderBuilder {
	private RmOrderBuilder() {}

	public static String orderRef(Order order) {
		if (order == null || order.permId() == 0) return null;
		String ref = order.orderRef();
		if (ref != null && !ref.isEmpty()) return ref;
		return "tws:" + order.permId();
	}

	public static String mapStatus(String ibStatus) {
		if (ibStatus == null) return null;
		switch (ibStatus) {
		case "ApiPending":
		case "PendingSubmit":
		case "PreSubmitted":
		case "Submitted":
		case "Inactive":
		case "Unknown":
			return "new";
		case "PendingCancel":
		case "PreCancelled":
			return "cancelling";
		case "Filled":
			return "filled";
		case "Rejected":
			return "rejected";
		case "Cancelled":
		case "Canceled":
		case "ApiCancelled":
			return "cancelled";
		default:
			return null;
		}
	}

	public static boolean terminal(String rmStatus) {
		return "filled".equals(rmStatus) || "cancelled".equals(rmStatus) || "rejected".equals(rmStatus);
	}

	public static Map<String, Object> build(
			Contract contract, Order order, String ibStatus,
			Decimal filled, Decimal remaining,
			double avg, boolean zeroAvgWhenNoFill,
			BigDecimal commission, String commissionCurrency,
			Map<Integer, Contract> legDetails) {
		String ref = orderRef(order);
		String status = mapStatus(ibStatus);
		if (ref == null || status == null || contract == null) return null;

		BigDecimal qty = quantity(order, filled, remaining);
		BigDecimal filledQty = decimalOrZero(filled);
		Object remained = terminal(status) ? BigDecimal.ZERO : qty.subtract(filledQty);

		Map<String, Object> doc = new LinkedHashMap<>();
		doc.put("orderRef", ref);
		doc.put("account", order.account());
		doc.put("status", status);
		doc.put("side", side(order.action()));
		doc.put("orderType", order.getOrderType());
		doc.put("price", price(contract, order));
		doc.put("qty", qty);
		doc.put("filledQty", filledQty);
		doc.put("remainedQty", remained);
		doc.put("avgPrice", avgPrice(avg, zeroAvgWhenNoFill));
		doc.put("currency", contract.currency());
		doc.put("commission", commission == null ? BigDecimal.ZERO : commission);
		doc.put("commissionCurrency", commissionCurrency != null ? commissionCurrency : contract.currency());
		doc.put("secType", RmPair.secType(contract));
		doc.put("pair", RmPair.pair(contract));
		doc.put("exchange", RmPair.orderExchange(contract));
		doc.put("multiplier", RmPair.multiplierNumber(contract));
		List<Map<String, Object>> legs = legs(contract, order, qty, legDetails);
		if (legs != null) doc.put("comboLegs", legs);
		return doc;
	}

	private static Object price(Contract contract, Order order) {
		if ("BAG".equals(RmPair.secType(contract)))
			return RmPair.usablePrice(order.lmtPrice()) ? order.lmtPrice() : null;
		OrderType kind = OrderType.get(order.getOrderType());
		switch (kind) {
		case LMT:
		case STP_LMT:
		case LOC:
			return RmPair.usablePrice(order.lmtPrice()) ? order.lmtPrice() : null;
		case STP:
			return RmPair.usablePrice(order.auxPrice()) ? order.auxPrice() : null;
		case MKT:
		case TRAIL:
		case TRAIL_LIMIT:
			return null;
		default:
			return null;
		}
	}

	private static Object avgPrice(double avg, boolean zeroWhenNoFill) {
		if (RmPair.usablePrice(avg)) return avg;
		if (zeroWhenNoFill || avg == 0d) return zeroWhenNoFill || avg == 0d ? Integer.valueOf(0) : null;
		return null;
	}

	static BigDecimal quantity(Order order, Decimal filled, Decimal remaining) {
		Decimal total = order == null ? null : order.totalQuantity();
		if (total != null && total.isValid() && !total.isZero()) return total.value();
		BigDecimal f = decimalOrNull(filled);
		BigDecimal r = decimalOrNull(remaining);
		if (f != null || r != null) {
			BigDecimal sum = (f == null ? BigDecimal.ZERO : f).add(r == null ? BigDecimal.ZERO : r);
			if (sum.signum() != 0) return sum;
		}
		if (total != null && total.isValid()) return total.value();
		return BigDecimal.ZERO;
	}

	private static BigDecimal decimalOrZero(Decimal d) {
		BigDecimal n = decimalOrNull(d);
		return n == null ? BigDecimal.ZERO : n;
	}

	private static BigDecimal decimalOrNull(Decimal d) {
		if (d == null || !d.isValid()) return null;
		return d.value();
	}

	static String side(Action action) {
		if (action == Action.BUY) return "BUY";
		if (action == Action.SELL || action == Action.SSHORT) return "SELL";
		return null;
	}

	static String side(String action) {
		if (action == null) return null;
		return side(Action.get(action));
	}

	private static List<Map<String, Object>> legs(
			Contract contract, Order order, BigDecimal parentQty, Map<Integer, Contract> legDetails) {
		List<ComboLeg> combo = contract.comboLegs();
		if (combo == null || combo.isEmpty()) return null;
		List<OrderComboLeg> prices = order.orderComboLegs();
		List<Map<String, Object>> out = new ArrayList<>();
		for (int i = 0; i < combo.size(); i++) {
			ComboLeg leg = combo.get(i);
			Contract known = legDetails == null || leg.conid() == 0 ? null : legDetails.get(leg.conid());
			double rawPrice = Double.MAX_VALUE;
			if (prices != null && i < prices.size() && prices.get(i) != null) rawPrice = prices.get(i).price();
			Map<String, Object> row = new LinkedHashMap<>();
			if (leg.conid() != 0) row.put("conId", leg.conid());
			row.put("pair", known == null ? null : RmPair.pair(known));
			row.put("side", side(leg.getAction()));
			row.put("qty", parentQty.multiply(BigDecimal.valueOf(leg.ratio())));
			row.put("price", RmPair.usablePrice(rawPrice) ? rawPrice : null);
			row.put("exchange", known != null ? RmPair.orderExchange(known) : RmPair.venue(leg.exchange()));
			row.put("secType", known == null ? null : RmPair.secType(known));
			row.put("multiplier", known == null ? null : RmPair.multiplierNumber(known));
			out.add(row);
		}
		return out;
	}
}
