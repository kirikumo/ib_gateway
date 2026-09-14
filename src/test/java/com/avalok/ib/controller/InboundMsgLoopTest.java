package com.avalok.ib.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

public class InboundMsgLoopTest {
	@Test
	public void errorDoesNotStopDrain() {
		AtomicInteger calls = new AtomicInteger();
		List<Exception> errors = new ArrayList<>();
		InboundMsgLoop.drain(() -> true, () -> {
			int n = calls.incrementAndGet();
			if (n == 1)
				throw new NoClassDefFoundError("TopMktDataHandler$4");
			if (n == 2)
				return;
			Assert.fail("should stop after successful processMsgs");
		}, errors::add);
		Assert.assertEquals(2, calls.get());
		Assert.assertEquals(1, errors.size());
		Assert.assertEquals("IB message loop", errors.get(0).getMessage());
		Assert.assertTrue(errors.get(0).getCause() instanceof NoClassDefFoundError);
	}

	@Test
	public void runtimeExceptionDoesNotStopDrain() {
		AtomicInteger calls = new AtomicInteger();
		List<Exception> errors = new ArrayList<>();
		InboundMsgLoop.drain(() -> true, () -> {
			if (calls.incrementAndGet() == 1)
				throw new RuntimeException("tick boom");
		}, errors::add);
		Assert.assertEquals(2, calls.get());
		Assert.assertEquals(1, errors.size());
		Assert.assertEquals("tick boom", errors.get(0).getMessage());
	}

	@Test
	public void ioExceptionStopsDrain() {
		AtomicInteger calls = new AtomicInteger();
		List<Exception> errors = new ArrayList<>();
		InboundMsgLoop.drain(() -> true, () -> {
			calls.incrementAndGet();
			throw new IOException("socket");
		}, errors::add);
		Assert.assertEquals(1, calls.get());
		Assert.assertEquals(1, errors.size());
		Assert.assertTrue(errors.get(0) instanceof IOException);
	}
}
