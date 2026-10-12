package com.avalok.ib.rm;

public final class RmKeys {
	private RmKeys() {}

	public static String connection(String gateway) { return "RM:IBKR:" + gateway + ":connection"; }
	public static String open(String account) { return "RM:IBKR:" + account + ":open"; }
	public static String order(String account) { return "RM:IBKR:" + account + ":order"; }
	public static String balance(String account) { return "RM:IBKR:" + account + ":balance"; }
	public static String summary(String account) { return "RM:IBKR:" + account + ":summary"; }
}
