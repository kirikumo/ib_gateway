package com.avalok.ib.controller;

import com.ib.client.Decimal;

/** Status strings absent from the OrderStatus enum, such as PreCancelled, WarnState, Rejected, and Canceled. */
public interface RawOrderStatusListener {
	void onRawOrderStatus(int orderId, String status, Decimal filled, Decimal remaining, double avgFillPrice, long permId);
}
