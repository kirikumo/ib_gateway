package com.avalok.ib.rm;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

import com.ib.client.Contract;
import com.ib.client.Decimal;
import com.ib.controller.Position;

/**
 * Writes both keys only on this round's accountDownloadEnd, when AccountReady was not false
 * and an unsuffixed NetLiquidation row is present.
 */
public final class RmAccountFeed {
	public enum Outcome { END, TIMEOUT, DISCONNECT }

	private final RmStore store;
	private final Object lock = new Object();
	private String roundAccount;
	private boolean roundOpen;
	private boolean ended;
	private boolean writable = true;
	private final Map<String, BigDecimal> cash = new LinkedHashMap<>();
	private final Map<String, Map<String, Object>> positions = new LinkedHashMap<>();
	private BigDecimal netLiq;
	private String baseCurrency;
	private boolean sawNlv;

	public RmAccountFeed(RmStore store) {
		this.store = store;
	}

	public void beginRound(String account) {
		synchronized (lock) {
			roundAccount = account;
			roundOpen = true;
			ended = false;
			writable = true;
			cash.clear();
			positions.clear();
			netLiq = null;
			baseCurrency = null;
			sawNlv = false;
		}
	}

	/** Whether this round may still be written. False after AccountReady=false. */
	public boolean isRoundWritable() {
		synchronized (lock) {
			return roundOpen && writable;
		}
	}

	public void onValue(String account, String key, String value, String currency) {
		synchronized (lock) {
			if (!roundOpen || account == null || !account.equals(roundAccount)) return;
			if ("AccountReady".equals(key) && value != null && "false".equalsIgnoreCase(value)) {
				writable = false;
				return;
			}
			if ("CashBalance".equals(key)) {
				if (currency == null || currency.isEmpty() || "BASE".equals(currency)) return;
				BigDecimal n = parse(value);
				if (n == null) return;
				cash.put(currency, n);
				return;
			}
			if ("NetLiquidation".equals(key) && unsuffixed(currency)) {
				BigDecimal n = parse(value);
				if (n == null) return;
				netLiq = n;
				baseCurrency = currency;
				sawNlv = true;
			}
		}
	}

	public void onPortfolio(Position position) {
		if (position == null || position.contract() == null) return;
		synchronized (lock) {
			if (!roundOpen || position.account() == null || !position.account().equals(roundAccount)) return;
			Contract c = position.contract();
			Decimal pos = position.position();
			if (pos == null || !pos.isValid() || pos.isZero()) return;
			String key = RmPair.positionKey(c);
			if (key == null) return;
			boolean priced = RmPair.usablePrice(position.marketPrice());
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("pos", pos.value());
			row.put("marketValue", priced ? Double.valueOf(position.marketValue()) : null);
			row.put("marketPrice", priced ? Double.valueOf(position.marketPrice()) : null);
			row.put("secType", RmPair.secType(c));
			row.put("currency", c.currency());
			row.put("pair", RmPair.pair(c));
			row.put("multiplier", RmPair.multiplierNumber(c));
			positions.put(key, row);
		}
	}

	public void onDownloadEnd(String account) {
		onDownloadEnd(account, System.currentTimeMillis());
	}

	/** Test only. Supplies the snapshot time. Production calls {@link #onDownloadEnd(String)}. */
	public void onDownloadEnd(String account, long now) {
		synchronized (lock) {
			if (!roundOpen || account == null || !account.equals(roundAccount)) return;
			ended = true;
			roundOpen = false;
			if (writable && sawNlv) {
				Map<String, Object> cashOut = new LinkedHashMap<>();
				for (Map.Entry<String, BigDecimal> e : cash.entrySet()) {
					Map<String, Object> row = new LinkedHashMap<>();
					row.put("balance", e.getValue());
					cashOut.put(e.getKey(), row);
				}
				Map<String, Object> balance = new LinkedHashMap<>();
				balance.put("updateTimeMs", now);
				balance.put("cash", cashOut);
				balance.put("positions", new LinkedHashMap<>(positions));
				Map<String, Object> summary = new LinkedHashMap<>();
				summary.put("updateTimeMs", now);
				summary.put("netLiquidation", netLiq);
				summary.put("baseCurrency", baseCurrency);
				store.set(RmKeys.balance(account), RmJson.toJson(balance));
				store.set(RmKeys.summary(account), RmJson.toJson(summary));
			}
			lock.notifyAll();
		}
	}

	public Outcome awaitEnd(String account, long timeoutMs, BooleanSupplier connected) {
		long deadline = System.currentTimeMillis() + timeoutMs;
		synchronized (lock) {
			try {
				while (roundOpen && !ended && connected.getAsBoolean() && System.currentTimeMillis() < deadline) {
					long wait = deadline - System.currentTimeMillis();
					if (wait <= 0) break;
					lock.wait(wait);
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			if (ended) return Outcome.END;
			roundOpen = false;
			cash.clear();
			positions.clear();
			sawNlv = false;
			if (!connected.getAsBoolean()) return Outcome.DISCONNECT;
			return Outcome.TIMEOUT;
		}
	}

	private static boolean unsuffixed(String currency) {
		if (currency == null || currency.isEmpty()) return false;
		return !currency.endsWith("-S") && !currency.endsWith("-C");
	}

	private static BigDecimal parse(String value) {
		if (value == null || value.isEmpty()) return null;
		try {
			return new BigDecimal(value);
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
