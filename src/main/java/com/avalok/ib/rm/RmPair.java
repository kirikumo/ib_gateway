package com.avalok.ib.rm;

import java.math.BigDecimal;
import java.util.Locale;

import com.ib.client.Contract;
import com.ib.client.Types.Right;

/** Risk pair and position key. Does not use IBContract.pair(). */
public final class RmPair {
	private RmPair() {}

	public static String secType(Contract c) {
		if (c == null) return null;
		String s = c.getSecType();
		if (s == null || s.isEmpty() || "None".equals(s)) return null;
		return s;
	}

	/** Non-empty and not SMART. */
	public static String venue(String v) {
		if (v == null) return null;
		String t = v.trim();
		if (t.isEmpty() || "SMART".equals(t)) return null;
		return t;
	}

	/** Only an 8-digit lastTradeDate. Do not fall back to the contract month. */
	public static String ymd(String lastTradeDate) {
		if (lastTradeDate != null && lastTradeDate.matches("\\d{8}")) return lastTradeDate;
		return null;
	}

	public static String pair(Contract c) {
		String sec = secType(c);
		if (sec == null || c == null) return null;
		switch (sec) {
		case "STK":
		case "CASH":
			if (empty(c.currency()) || empty(c.symbol())) return null;
			return c.currency() + "-" + c.symbol();
		case "FUT": {
			String date = ymd(c.lastTradeDate());
			String mult = multiplierToken(c.multiplier());
			if (date == null || mult == null || empty(c.currency()) || empty(c.symbol())) return null;
			return c.currency() + "-" + c.symbol() + "@" + date + "@" + mult;
		}
		case "OPT":
		case "FOP": {
			String date = ymd(c.lastTradeDate());
			String mult = multiplierToken(c.multiplier());
			String right = rightCode(c.right());
			if (date == null || mult == null || right == null || c.conid() == 0
					|| empty(c.currency()) || empty(c.symbol())) return null;
			String strike = String.format(Locale.US, "%.1f", c.strike());
			return c.currency() + "-" + c.symbol() + "@" + date + "@" + mult + right + strike + "@" + c.conid();
		}
		case "BOND":
			if (empty(c.tradingClass()) || c.conid() == 0) return null;
			return c.tradingClass() + "-" + c.conid();
		default:
			return null;
		}
	}

	public static String positionKey(Contract c) {
		String sec = secType(c);
		if (sec == null || "BAG".equals(sec)) return null;
		String pair = pair(c);
		String primary = venue(c.primaryExch());
		String exch = venue(c.exchange());
		int conId = c.conid();
		if (pair != null) {
			if (primary != null) return primary + ":" + sec + ":" + pair;
			if (exch != null) return exch + ":" + sec + ":" + pair;
			if (conId != 0) return sec + ":conid:" + conId;
			return null;
		}
		if (conId == 0) return null;
		String venue = primary != null ? primary : exch;
		if (venue != null) return venue + ":" + sec + ":conid:" + conId;
		return sec + ":conid:" + conId;
	}

	/** Order exchange: primary listing. Futures with no primary use the contract exchange. */
	public static String orderExchange(Contract c) {
		String primary = venue(c == null ? null : c.primaryExch());
		if (primary != null) return primary;
		if ("FUT".equals(secType(c))) return venue(c.exchange());
		return null;
	}

	public static Object multiplierNumber(Contract c) {
		String token = multiplierToken(c == null ? null : c.multiplier());
		if (token == null) return "STK".equals(secType(c)) ? BigDecimal.ONE : null;
		return new BigDecimal(token);
	}

	public static String multiplierToken(String raw) {
		if (raw == null || raw.trim().isEmpty()) return null;
		try {
			return new BigDecimal(raw.trim()).stripTrailingZeros().toPlainString();
		} catch (NumberFormatException e) {
			return null;
		}
	}

	public static boolean usablePrice(double v) {
		return Double.isFinite(v) && v != 0d && v != Double.MAX_VALUE;
	}

	private static String rightCode(Right right) {
		if (right == Right.Call) return "C";
		if (right == Right.Put) return "P";
		return null;
	}

	private static boolean empty(String s) {
		return s == null || s.isEmpty();
	}
}
