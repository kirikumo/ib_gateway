package com.avalok.ib.rm;

import java.util.List;
import java.util.function.Consumer;

/**
 * Stop the previous subscription, subscribe, then wait for End, timeout, or disconnect
 * before the next account. The wait runs on the caller thread, not in the one-second heartbeat.
 */
public final class RmAccountCycle {
	/** Stuck-account cap, not the update interval in the contract. */
	public static final long DEFAULT_TIMEOUT_MS = 60_000L;

	private final long timeoutMs;
	private String subscribed;

	public RmAccountCycle() {
		this(DEFAULT_TIMEOUT_MS);
	}

	/** Test only. Production uses {@link #RmAccountCycle()} and the 60s cap. */
	public RmAccountCycle(long timeoutMs) {
		this.timeoutMs = timeoutMs;
	}

	public interface Updates {
		void subscribe(boolean on, String account);
		boolean connected();
	}

	public void run(List<String> accounts, String focus, Updates api, RmAccountFeed feed) {
		run(accounts, focus, api, feed, s -> {});
	}

	public void run(List<String> accounts, String focus, Updates api, RmAccountFeed feed, Consumer<String> log) {
		String last = "";
		if (accounts != null) {
			for (String account : accounts) {
				if (account == null || account.isEmpty()) continue;
				if (!one(account, api, feed, log)) return;
				last = account;
			}
		}
		if (focus != null && !focus.isEmpty() && !focus.equals(last))
			one(focus, api, feed, log);
	}

	private boolean one(String account, Updates api, RmAccountFeed feed, Consumer<String> log) {
		if (!api.connected()) {
			log.accept("Account MV cycle aborted, disconnected");
			return false;
		}
		if (subscribed != null) {
			log.accept("--> Stop account mv " + subscribed);
			api.subscribe(false, subscribed);
		}
		feed.beginRound(account);
		log.accept("--> Req account mv " + account);
		api.subscribe(true, account);
		subscribed = account;
		RmAccountFeed.Outcome outcome = feed.awaitEnd(account, timeoutMs, api::connected);
		if (outcome == RmAccountFeed.Outcome.DISCONNECT) {
			log.accept("Account MV cycle aborted, disconnected");
			return false;
		}
		return api.connected();
	}
}
