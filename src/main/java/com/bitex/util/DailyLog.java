package com.bitex.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Copies this JVM's stdout and stderr into logs/&lt;gateway&gt;/ib_gateway.yyyy-MM-dd.log.
 */
public final class DailyLog {
	private static final Object LOCK = new Object();
	private static final Pattern ANSI = Pattern.compile("\u001B\\[[0-9;]*m");
	private static final Pattern LOG_FILE = Pattern.compile("ib_gateway\\.(\\d{4}-\\d{2}-\\d{2})\\.log");
	private static final int KEEP_DAYS = 10;

	// Test only
	static volatile Path baseDir = Path.of("logs");
	// Test only
	static volatile LocalDate todayOverride;
	// Test only. When true, testName is used even if it is null.
	static volatile boolean nameFromTest;
	// Test only
	static volatile String testName;

	private static PrintStream originalOut;
	private static PrintStream originalErr;
	private static boolean installed;
	private static String gatewayDirName;
	private static LocalDate openDate;
	private static Object openFileKey;
	private static java.io.BufferedWriter writer;

	private DailyLog() {}

	public static void install() {
		synchronized (LOCK) {
			if (installed) return;
			gatewayDirName = currentDirectoryName();
			originalOut = System.out;
			originalErr = System.err;
			System.setOut(new PrintStream(new Tee(originalOut), true, StandardCharsets.UTF_8));
			System.setErr(new PrintStream(new Tee(originalErr), true, StandardCharsets.UTF_8));
			installed = true;
		}
	}

	/** Test only. */
	static void useTestDir(Path dir) {
		baseDir = dir;
	}

	/** Test only. */
	static void useToday(LocalDate day) {
		todayOverride = day;
	}

	/** Test only. null means the gateway name is unset. */
	static void useGatewayName(String name) {
		nameFromTest = true;
		testName = name;
	}

	/** Test only. */
	static void restore() {
		synchronized (LOCK) {
			if (installed) {
				System.setOut(originalOut);
				System.setErr(originalErr);
			}
			closeWriter();
			installed = false;
			gatewayDirName = null;
			baseDir = Path.of("logs");
			todayOverride = null;
			nameFromTest = false;
			testName = null;
		}
	}

	static String directoryName(String raw) {
		if (raw == null) return "unknown";
		String trimmed = raw.trim();
		if (trimmed.isEmpty()) return "unknown";
		StringBuilder sb = new StringBuilder(trimmed.length());
		for (int i = 0; i < trimmed.length(); i++) {
			char c = trimmed.charAt(i);
			if (isNameChar(c)) sb.append(c);
			else sb.append('_');
		}
		String name = sb.toString();
		if (name.isEmpty() || ".".equals(name) || "..".equals(name)) return "unknown";
		return name;
	}

	private static boolean isNameChar(char c) {
		return (c >= 'A' && c <= 'Z')
				|| (c >= 'a' && c <= 'z')
				|| (c >= '0' && c <= '9')
				|| c == '_' || c == '-' || c == '.';
	}

	private static String currentDirectoryName() {
		String raw = nameFromTest ? testName : System.getenv("TWS_GATEWAY_NAME");
		return directoryName(raw);
	}

	private static LocalDate today() {
		LocalDate override = todayOverride;
		if (override != null) return override;
		return LocalDate.now(ZoneId.systemDefault());
	}

	private static void writeLine(byte[] raw) {
		synchronized (LOCK) {
			if (!installed) return;
			String text = new String(raw, StandardCharsets.UTF_8);
			if (text.endsWith("\r")) text = text.substring(0, text.length() - 1);
			text = ANSI.matcher(text).replaceAll("");
			try {
				ensureOpen();
				if (writer == null) return;
				writer.write(text);
				writer.write('\n');
				writer.flush();
			} catch (IOException ignored) {
				closeWriter();
			}
		}
	}

	private static void ensureOpen() throws IOException {
		LocalDate today = today();
		Path dir = baseDir.resolve(gatewayDirName);
		Path path = dir.resolve("ib_gateway." + today + ".log");
		// A deleted or replaced path still holds the old descriptor.
		if (writer != null && today.equals(openDate) && sameOpenFile(path)) return;
		closeWriter();
		Files.createDirectories(dir);
		writer = Files.newBufferedWriter(
				path,
				StandardCharsets.UTF_8,
				StandardOpenOption.CREATE,
				StandardOpenOption.APPEND);
		openFileKey = fileKey(path);
		openDate = today;
		try {
			purge(dir, today);
		} catch (IOException ignored) {
			// The line still has to land. The next open tries cleanup again.
		}
	}

	private static void purge(Path dir, LocalDate today) throws IOException {
		LocalDate cutoff = today.minusDays(KEEP_DAYS - 1);
		List<Path> old = new ArrayList<>();
		try (var list = Files.list(dir)) {
			for (Path path : list.collect(Collectors.toList())) {
				Matcher matcher = LOG_FILE.matcher(path.getFileName().toString());
				if (!matcher.matches()) continue;
				LocalDate fileDay;
				try {
					fileDay = LocalDate.parse(matcher.group(1));
				} catch (DateTimeParseException ignored) {
					continue;
				}
				if (fileDay.isBefore(cutoff)) old.add(path);
			}
		}
		for (Path path : old) Files.deleteIfExists(path);
	}

	/** Path missing, or another file now occupies it. A null file key can only prove the path exists. */
	private static boolean sameOpenFile(Path path) {
		try {
			if (!Files.exists(path)) return false;
			if (openFileKey == null) return true;
			Object now = fileKey(path);
			if (now == null) return true;
			return openFileKey.equals(now);
		} catch (IOException e) {
			return false;
		}
	}

	private static Object fileKey(Path path) throws IOException {
		return Files.readAttributes(path, BasicFileAttributes.class).fileKey();
	}

	private static void closeWriter() {
		if (writer != null) {
			try {
				writer.close();
			} catch (IOException ignored) {
			}
			writer = null;
		}
		openFileKey = null;
		openDate = null;
	}

	/**
	 * One buffer per stream, so a stdout line and a stderr line cannot splice into each other.
	 */
	private static final class Tee extends OutputStream {
		private final PrintStream terminal;
		private final ByteArrayOutputStream pending = new ByteArrayOutputStream();

		Tee(PrintStream terminal) {
			this.terminal = terminal;
		}

		@Override
		public void write(int b) {
			write(new byte[] { (byte) b }, 0, 1);
		}

		@Override
		public void write(byte[] b, int off, int len) {
			try {
				terminal.write(b, off, len);
				terminal.flush();
			} catch (Throwable ignored) {
			}
			for (int i = off; i < off + len; i++) {
				byte c = b[i];
				if (c == '\n') {
					byte[] line = pending.toByteArray();
					pending.reset();
					writeLine(line);
				} else {
					pending.write(c);
				}
			}
		}
	}
}
