package com.avalok.ib.rm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

public class RmAccountCycleTest {

	@Test
	public void secondSubscribeWaitsForFirstEnd() {
		MemoryRmStore store = new MemoryRmStore();
		RmAccountFeed feed = new RmAccountFeed(store);
		Fake api = new Fake(feed);
		api.autoEnd.add("A");
		api.autoEnd.add("B");
		new RmAccountCycle(50).run(List.of("A", "B"), "", api, feed);
		int endA = api.events.indexOf("END:A");
		int trueB = api.events.indexOf("T:B");
		Assert.assertTrue(endA >= 0 && endA < trueB);
		Assert.assertEquals("T:A", api.events.get(0));
		Assert.assertTrue(api.events.indexOf("F:A") < trueB);
		Assert.assertFalse(api.events.get(0).startsWith("F:"));
	}

	@Test
	public void timeoutWritesNoRmKeyAndContinues() {
		MemoryRmStore store = new MemoryRmStore();
		RmAccountFeed feed = new RmAccountFeed(store);
		Fake api = new Fake(feed);
		api.pushNlv.add("A");
		api.pushNlv.add("B");
		new RmAccountCycle(40).run(List.of("A", "B"), "", api, feed);
		Assert.assertNull(store.get(RmKeys.balance("A")));
		Assert.assertNull(store.get(RmKeys.summary("A")));
		Assert.assertNull(store.get(RmKeys.balance("B")));
		Assert.assertTrue(api.events.indexOf("T:A") < api.events.indexOf("F:A"));
		Assert.assertTrue(api.events.indexOf("F:A") < api.events.indexOf("T:B"));
	}

	@Test
	public void disconnectStopsTheCycle() {
		MemoryRmStore store = new MemoryRmStore();
		RmAccountFeed feed = new RmAccountFeed(store);
		Fake api = new Fake(feed);
		api.dropOn = "A";
		new RmAccountCycle(50).run(List.of("A", "B"), "", api, feed);
		Assert.assertTrue(api.events.contains("T:A"));
		Assert.assertFalse(api.events.contains("T:B"));
	}

	@Test
	public void focusNotLastIsSubscribedAfterTheRound() {
		MemoryRmStore store = new MemoryRmStore();
		RmAccountFeed feed = new RmAccountFeed(store);
		Fake api = new Fake(feed);
		api.autoEnd.add("A");
		api.autoEnd.add("B");
		new RmAccountCycle(50).run(List.of("A", "B"), "A", api, feed);
		Assert.assertEquals(List.of("T:A", "END:A", "F:A", "T:B", "END:B", "F:B", "T:A", "END:A"), api.events);
		Assert.assertTrue(api.events.indexOf("END:A") < api.events.indexOf("T:B"));
	}

	@Test
	public void rememberedSubscriptionIsStoppedOnTheNextRun() {
		MemoryRmStore store = new MemoryRmStore();
		RmAccountFeed feed = new RmAccountFeed(store);
		Fake api = new Fake(feed);
		api.autoEnd.add("A");
		RmAccountCycle cycle = new RmAccountCycle(50);
		cycle.run(List.of("A"), "", api, feed);
		api.events.clear();
		cycle.run(List.of("A"), "", api, feed);
		Assert.assertEquals("F:A", api.events.get(0));
		Assert.assertEquals("T:A", api.events.get(1));
	}

	@Test
	public void heartbeatTaskDoesNotSleep() throws Exception {
		String text = Files.readString(Path.of("src/main/java/com/avalok/ib/GatewayController.java"));
		int from = text.indexOf("GatewayControllerLiveStatusWriter");
		int to = text.indexOf("GatewayControllerAccountMVRefresh");
		Assert.assertTrue(from >= 0 && to > from);
		Assert.assertFalse(text.substring(from, to).contains("sleep"));
		Assert.assertFalse(text.contains("ACCOUNT_MV_ACCOUNT_DELAY_MS"));
	}

	/** Test double for the account-update subscription. */
	private static final class Fake implements RmAccountCycle.Updates {
		final List<String> events = new ArrayList<>();
		final Set<String> autoEnd = new HashSet<>();
		final Set<String> pushNlv = new HashSet<>();
		final RmAccountFeed feed;
		boolean connected = true;
		String dropOn;

		Fake(RmAccountFeed feed) {
			this.feed = feed;
		}

		@Override
		public void subscribe(boolean on, String account) {
			events.add((on ? "T:" : "F:") + account);
			if (!on) return;
			if (pushNlv.contains(account) || autoEnd.contains(account))
				feed.onValue(account, "NetLiquidation", "10", "HKD");
			if (account.equals(dropOn)) connected = false;
			if (autoEnd.contains(account)) {
				feed.onDownloadEnd(account, 1000L);
				events.add("END:" + account);
			}
		}

		@Override
		public boolean connected() {
			return connected;
		}
	}
}
