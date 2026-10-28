package com.bitex.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DailyLogTest {
	private static final LocalDate DAY = LocalDate.of(2026, 10, 28);
	private static final Pattern TIME = Pattern.compile("\\d{2}/\\d{2}-\\d{2}:\\d{2}:\\d{2}\\.\\d{3} .+");

	private PrintStream realOut;
	private PrintStream realErr;
	private ByteArrayOutputStream outBuf;
	private ByteArrayOutputStream errBuf;
	private Path temp;
	private Path logs;

	@Before
	public void setUp() throws Exception {
		realOut = System.out;
		realErr = System.err;
		outBuf = new ByteArrayOutputStream();
		errBuf = new ByteArrayOutputStream();
		System.setOut(new PrintStream(outBuf, true, StandardCharsets.UTF_8));
		System.setErr(new PrintStream(errBuf, true, StandardCharsets.UTF_8));
		temp = Files.createTempDirectory("daily-log");
		logs = temp.resolve("logs");
		DailyLog.useTestDir(logs);
		DailyLog.useToday(DAY);
	}

	@After
	public void tearDown() throws Exception {
		DailyLog.restore();
		System.setOut(realOut);
		System.setErr(realErr);
		if (temp != null && Files.exists(temp)) {
			try (var walk = Files.walk(temp)) {
				walk.sorted(Comparator.reverseOrder()).forEach(path -> {
					try {
						Files.deleteIfExists(path);
					} catch (Exception ignored) {
					}
				});
			}
		}
	}

	@Test
	public void stdoutKeepsColorAndFileStripsIt() throws Exception {
		DailyLog.useGatewayName("mkdata_gateway");
		assertFalse(Files.exists(logs.resolve("mkdata_gateway")));
		DailyLog.install();
		System.out.println("\u001B[31mboom\u001B[0m");
		assertTrue(terminalOut().contains("\u001B[31mboom\u001B[0m"));
		assertEquals("boom\n", read("mkdata_gateway", DAY));
	}

	@Test
	public void stackTraceLandsInTheSameFile() throws Exception {
		DailyLog.useGatewayName("mkdata_gateway");
		DailyLog.install();
		new IllegalStateException("daily-log-marker").printStackTrace();
		String file = read("mkdata_gateway", DAY);
		assertTrue(terminalErr().contains("daily-log-marker"));
		assertTrue(file.contains("daily-log-marker"));
		assertTrue(file.contains("IllegalStateException"));
	}

	@Test
	public void secondInstallDoesNotDuplicateALine() throws Exception {
		DailyLog.useGatewayName("mkdata_gateway");
		DailyLog.install();
		DailyLog.install();
		System.out.println("only-once");
		assertEquals("only-once\n", read("mkdata_gateway", DAY));
	}

	@Test
	public void secondInstallKeepsTheFirstGatewayName() throws Exception {
		DailyLog.useGatewayName("mkdata_gateway");
		DailyLog.install();
		DailyLog.useGatewayName("angel4500_gateway");
		DailyLog.install();
		System.out.println("stays");
		assertEquals("stays\n", read("mkdata_gateway", DAY));
		assertFalse(Files.exists(logs.resolve("angel4500_gateway")));
	}

	@Test
	public void missingNameUsesUnknown() throws Exception {
		for (String name : new String[] { null, "   ", ".." }) {
			outBuf.reset();
			DailyLog.useTestDir(logs);
			DailyLog.useToday(DAY);
			DailyLog.useGatewayName(name);
			DailyLog.install();
			System.out.println("named-" + name);
			DailyLog.restore();
			System.setOut(new PrintStream(outBuf, true, StandardCharsets.UTF_8));
			System.setErr(new PrintStream(errBuf, true, StandardCharsets.UTF_8));
		}
		String file = read("unknown", DAY);
		assertTrue(file.contains("named-null"));
		assertTrue(file.contains("named-   "));
		assertTrue(file.contains("named-.."));
	}

	@Test
	public void twoThreadsKeepWholeLines() throws Exception {
		DailyLog.useGatewayName("mkdata_gateway");
		DailyLog.install();
		CountDownLatch start = new CountDownLatch(1);
		List<Thread> threads = new ArrayList<>();
		for (String line : new String[] { "THREAD-A-LINE", "THREAD-B-LINE" }) {
			threads.add(new Thread(() -> {
				try {
					start.await();
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
				System.out.println(line);
			}));
		}
		for (Thread thread : threads) thread.start();
		start.countDown();
		for (Thread thread : threads) thread.join();
		String file = read("mkdata_gateway", DAY);
		assertTrue(file.contains("THREAD-A-LINE\n"));
		assertTrue(file.contains("THREAD-B-LINE\n"));
		assertEquals(2, file.split("\n", -1).length - 1);
	}

	@Test
	public void gatewaysWriteSeparateDirectories() throws Exception {
		DailyLog.useGatewayName("mkdata_gateway");
		DailyLog.install();
		System.out.println("from-mk");
		DailyLog.restore();
		System.setOut(new PrintStream(outBuf, true, StandardCharsets.UTF_8));
		System.setErr(new PrintStream(errBuf, true, StandardCharsets.UTF_8));
		DailyLog.useTestDir(logs);
		DailyLog.useToday(DAY);
		DailyLog.useGatewayName("angel4500_gateway");
		DailyLog.install();
		System.out.println("from-angel");
		assertEquals("from-mk\n", read("mkdata_gateway", DAY));
		assertEquals("from-angel\n", read("angel4500_gateway", DAY));
	}

	@Test
	public void rollsAtLocalMidnightAndAppendsSameDay() throws Exception {
		DailyLog.useGatewayName("mkdata_gateway");
		DailyLog.install();
		System.out.println("day28");
		String beforeRoll = read("mkdata_gateway", DAY);
		DailyLog.useToday(LocalDate.of(2026, 10, 29));
		assertFalse(Files.exists(logFile("mkdata_gateway", LocalDate.of(2026, 10, 29))));
		System.out.println("day29");
		assertEquals(beforeRoll, read("mkdata_gateway", DAY));
		assertEquals("day29\n", read("mkdata_gateway", LocalDate.of(2026, 10, 29)));

		DailyLog.restore();
		System.setOut(new PrintStream(outBuf, true, StandardCharsets.UTF_8));
		System.setErr(new PrintStream(errBuf, true, StandardCharsets.UTF_8));
		DailyLog.useTestDir(logs);
		DailyLog.useToday(DAY);
		DailyLog.useGatewayName("mkdata_gateway");
		DailyLog.install();
		System.out.println("again");
		assertEquals("day28\nagain\n", read("mkdata_gateway", DAY));
	}

	@Test
	public void deletedFileIsRecreatedOnNextLine() throws Exception {
		DailyLog.useGatewayName("mkdata_gateway");
		DailyLog.install();
		System.out.println("before-delete");
		Path file = logFile("mkdata_gateway", DAY);
		Files.delete(file);
		assertFalse(Files.exists(file));
		System.out.println("after-delete");
		assertEquals("after-delete\n", read("mkdata_gateway", DAY));
	}

	@Test
	public void deletedFileStaysGoneUntilTheNextLine() throws Exception {
		DailyLog.useGatewayName("mkdata_gateway");
		DailyLog.install();
		System.out.println("before-delete");
		Path file = logFile("mkdata_gateway", DAY);
		Files.delete(file);
		assertFalse(Files.exists(file));
	}

	@Test
	public void replacedFileReceivesTheNextLine() throws Exception {
		DailyLog.useGatewayName("mkdata_gateway");
		DailyLog.install();
		System.out.println("before-delete");
		Path file = logFile("mkdata_gateway", DAY);
		Files.delete(file);
		Files.writeString(file, "other\n");
		System.out.println("after-replace");
		String text = read("mkdata_gateway", DAY);
		assertTrue(text.contains("after-replace"));
		assertFalse(text.contains("before-delete"));
	}

	@Test
	public void truncatingTheSameFileKeepsThatFile() throws Exception {
		DailyLog.useGatewayName("mkdata_gateway");
		DailyLog.install();
		System.out.println("before-truncate");
		Path file = logFile("mkdata_gateway", DAY);
		Object key = Files.readAttributes(file, BasicFileAttributes.class).fileKey();
		try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
			channel.truncate(0);
		}
		System.out.println("after-truncate");
		assertEquals(key, Files.readAttributes(file, BasicFileAttributes.class).fileKey());
		assertEquals("after-truncate\n", read("mkdata_gateway", DAY));
	}

	@Test
	public void keepsTenDatesAndLeavesOtherGatewaysAlone() throws Exception {
		Path mine = logs.resolve("mkdata_gateway");
		Path other = logs.resolve("angel4500_gateway");
		Files.createDirectories(mine);
		Files.createDirectories(other);
		Files.writeString(mine.resolve("ib_gateway.2026-10-18.log"), "old");
		Files.writeString(mine.resolve("ib_gateway.2026-10-19.log"), "stay");
		Files.writeString(mine.resolve("notes.txt"), "note");
		Files.writeString(other.resolve("ib_gateway.2026-10-18.log"), "keep");
		DailyLog.useGatewayName("mkdata_gateway");
		DailyLog.install();
		System.out.println("today");
		assertFalse(Files.exists(mine.resolve("ib_gateway.2026-10-18.log")));
		assertEquals("stay", Files.readString(mine.resolve("ib_gateway.2026-10-19.log")));
		assertEquals("note", Files.readString(mine.resolve("notes.txt")));
		assertEquals("keep", Files.readString(other.resolve("ib_gateway.2026-10-18.log")));
		assertEquals("today\n", read("mkdata_gateway", DAY));
	}

	@Test
	public void fileFailureStillPrints() throws Exception {
		Path blocker = temp.resolve("blocker");
		Files.writeString(blocker, "x");
		DailyLog.useTestDir(blocker);
		DailyLog.useGatewayName("mkdata_gateway");
		DailyLog.install();
		System.out.println("still-here");
		assertTrue(terminalOut().contains("still-here"));
	}

	@Test
	public void logTimestampSurvivesConcurrentCalls() throws Exception {
		int threads = 8;
		int each = 40;
		CountDownLatch start = new CountDownLatch(1);
		AtomicReference<Throwable> failure = new AtomicReference<>();
		List<Thread> workers = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			int id = i;
			workers.add(new Thread(() -> {
				try {
					start.await();
					for (int n = 0; n < each; n++) DebugUtil.log("t" + id + "-" + n);
				} catch (Throwable t) {
					failure.set(t);
				}
			}));
		}
		for (Thread worker : workers) worker.start();
		start.countDown();
		for (Thread worker : workers) worker.join();
		assertTrue(failure.get() == null);
		String[] lines = terminalOut().split("\n");
		assertEquals(threads * each, lines.length);
		for (String line : lines) assertTrue(line, TIME.matcher(line).matches());
	}

	private String terminalOut() {
		return outBuf.toString(StandardCharsets.UTF_8);
	}

	private String terminalErr() {
		return errBuf.toString(StandardCharsets.UTF_8);
	}

	private Path logFile(String gateway, LocalDate day) {
		return logs.resolve(gateway).resolve("ib_gateway." + day + ".log");
	}

	private String read(String gateway, LocalDate day) throws Exception {
		return Files.readString(logFile(gateway, day));
	}
}
