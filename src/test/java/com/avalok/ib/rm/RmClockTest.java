package com.avalok.ib.rm;

import org.junit.Assert;
import org.junit.Test;

public class RmClockTest {

	@Test
	public void sameMillisecondIncrements() {
		RmClock clock = new RmClock();
		long first = clock.next("uranus_1", 1000L, null);
		long second = clock.next("uranus_1", 1000L, null);
		Assert.assertEquals(first + 1, second);
	}

	@Test
	public void readsPreviousTimeFromHashJson() {
		RmClock clock = new RmClock();
		String previous = "{\"updateTimeMs\":100,\"price\":null}";
		Assert.assertEquals(101L, clock.next("uranus_1", 100L, previous));
	}
}
