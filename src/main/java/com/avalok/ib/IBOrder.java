package com.avalok.ib;

import com.alibaba.fastjson.JSONArray;
import com.avalok.ib.handler.ContractDetailsHandler;
import static com.bitex.util.DebugUtil.*;

import com.ib.client.*;
import org.apache.commons.lang3.StringUtils;
import com.alibaba.fastjson.JSONObject;

import com.ib.client.Types.Action;

import java.util.ArrayList;

public class IBOrder {
	public IBContract contract;
	public JSONObject contractDetails;
	public Order order;
	// https://interactivebrokers.github.io/tws-api/order_submission.html#order_status
	protected OrderState orderState;
	boolean toBePlaced = false;
	
	/**
	 * Clone
	 */
	public IBOrder(IBOrder ibo) {
		contract = ibo.contract;
		contractDetails = ibo.contractDetails;
		order = ibo.order;
		orderState = ibo.orderState;
		toBePlaced = ibo.toBePlaced;
		filled = ibo.filled;
		remaining = ibo.remaining;
		avgFillPrice = ibo.avgFillPrice;
		lastFillPrice = ibo.lastFillPrice;
		mktCapPrice = ibo.mktCapPrice;
		whyHeld = ibo.whyHeld;
		statusFilled = ibo.statusFilled;
		extStatus = ibo.extStatus;
		extMsg = ibo.extMsg;
	}
	
	/**
	 * Deep clone IBOrder with real exchange instead of SMART 
	 * @return
	 */
	public IBOrder cloneWithRealExchange() {
		IBOrder o = new IBOrder(this);
//		IBContract ibc = o.contract;
//		if (ibc.exchange().equals("SMART")) {
//			IBContract ibcWithRealExchange = new IBContract(ibc);
//			ibcWithRealExchange.exchange(null);
//			Integer aggGroup = contractDetails.getInteger("aggGroup");
//
//			if (ContractDetailsHandler.fillSmartIBContract(ibcWithRealExchange, aggGroup)) {
//				String realExchange = ibcWithRealExchange.exchange();
//				// Clone an IBOrder with real exchange.
//				ibc = ibcWithRealExchange;
//				o = new IBOrder(o);
//				o.contract = ibc;
//				log("Auto replace exchange SMART with " + realExchange + "\n" + o);
//			} else {
//				err("Could not find real exchange for\n" + this);
//				return null;
//			}
//		}
		return o;
	}
	
	/**
	 * Build new order to place
	 */
	public IBOrder(Contract _contract, Order _order) {
		contract = new IBContract(_contract);
		contractDetails = ContractDetailsHandler.findDetails(contract);
		order = _order;
		toBePlaced = true;
	}
	/**
	 * Build new order to place,
	 * Parse full contract from j['contract']
	 * Parse URANUS format order from j['order']
	 */
	public IBOrder(JSONObject j) {
		contract = new IBContract(j.getJSONObject("contract"));
		contractDetails = ContractDetailsHandler.findDetails(contract);
		JSONObject oj = j.getJSONObject("order");
		order = new Order();
		order.account(oj.getString("account"));
		if (oj.getString("i") != null) // Only when modifying existed order.
			order.permId(Integer.parseInt(oj.getString("i")));
		order.action(oj.getString("T").toUpperCase()); // BUY SELL
		order.totalQuantity(Decimal.get(oj.getDoubleValue("s")));
		order.lmtPrice(oj.getDoubleValue("p"));
		// Use whatIf=true to check trading rules and margin
		// See https://interactivebrokers.github.io/tws-api/margin.html
		if (oj.getBoolean("whatIf") != null)
			order.whatIf(oj.getBoolean("whatIf"));
		if (oj.containsKey("executed"))
			order.filledQuantity(Decimal.get(oj.getDoubleValue("executed")));
		// Don't parse status, will get updates from IB after placing/modifying
		if (oj.containsKey("orderType"))
			order.orderType(oj.getString("orderType")); // Only place limit order
		else
			order.orderType("LMT"); // Only place limit order
		if (oj.getString("tif") != null) // Default: DAY
			order.tif(oj.getString("tif"));
		if (oj.getString("orderRef") != null)
			order.orderRef(oj.getString("orderRef"));
		if (oj.getBoolean("outsideRth") != null)
			order.outsideRth(oj.getBoolean("outsideRth"));

		if (oj.getString("algo") != null) {
//			https://interactivebrokers.github.io/tws-api/ibalgos.html
			order.algoStrategy(oj.getString("algo")); // Adaptive

			JSONArray algoParams = oj.getJSONArray("algoParams");
			if (algoParams != null) {
				order.algoParams(new ArrayList<>());
				for (int i = 0; i < algoParams.size(); i++) {
					JSONObject param = algoParams.getJSONObject(i);
					order.algoParams().add(new TagValue(param.getString("tag"), param.getString("value")));
				}
			}
		}

		setParams(oj);

		toBePlaced = true;
	}
	/**
	 * Build known order
	 */
	public IBOrder(Contract _contract, Order _order, OrderState _orderState) {
		contract = new IBContract(_contract);
		contractDetails = ContractDetailsHandler.findDetails(contract);
		order = _order;
		orderState = _orderState;
		fixIBBug01();
	}

	private void fixIBBug01() {
		// IB BUG fix, fuck totalQuantity
		if (orderState.status() == OrderStatus.Filled && order.totalQuantity().longValue() == 0) {
			order.totalQuantity(order.filledQuantity());
			warn("Fixing IB zero totalQuantity bug, fuck me -> " + order.totalQuantity().toString());
		}
	}

	public boolean isChildOrder() {
		return order != null && (order.parentId() != 0 || order.parentPermId() != 0);
	}

	// Published id. The IB field stays the broker string so placeOrder does not send the suffix.
	private String externalOrderRef() {
		if (order == null) return null;
		String ref = order.orderRef();
		if (!isChildOrder() || ref == null || ref.isEmpty()) return ref;
		long permId = order.permId();
		if (permId == 0) return null;
		String suffix = "@" + permId;
		if (ref.endsWith(suffix)) return ref;
		return ref + suffix;
	}

	public void setParams(JSONObject changeInfo) {
		if (changeInfo == null || order == null) return;
		if (changeInfo.containsKey("p")) {
			order.lmtPrice(changeInfo.getDoubleValue("p"));
		}
		if (changeInfo.containsKey("s")) {
			order.totalQuantity(Decimal.get(changeInfo.getDoubleValue("s")));
		}
		if (changeInfo.containsKey("scaleInitLevelSize")) {
			order.scaleInitLevelSize(changeInfo.getIntValue("scaleInitLevelSize"));
		}
		if (changeInfo.containsKey("scaleSubsLevelSize")) {
			order.scaleSubsLevelSize(changeInfo.getIntValue("scaleSubsLevelSize"));
		}
		if (changeInfo.containsKey("scalePriceIncrement")) {
			order.scalePriceIncrement(changeInfo.getDoubleValue("scalePriceIncrement"));
		}
		if (changeInfo.containsKey("scalePriceAdjustValue")) {
			order.scalePriceAdjustValue(changeInfo.getDoubleValue("scalePriceAdjustValue"));
		}
		if (changeInfo.containsKey("scalePriceAdjustInterval")) {
			order.scalePriceAdjustInterval(changeInfo.getIntValue("scalePriceAdjustInterval"));
		}
		if (changeInfo.containsKey("scaleProfitOffset")) {
			order.scaleProfitOffset(changeInfo.getDoubleValue("scaleProfitOffset"));
		}
		if (changeInfo.containsKey("scaleAutoReset")) {
			order.scaleAutoReset(changeInfo.getBooleanValue("scaleAutoReset"));
		}
		if (changeInfo.containsKey("scaleInitPosition")) {
			order.scaleInitPosition(changeInfo.getIntValue("scaleInitPosition"));
		}
		if (changeInfo.containsKey("scaleInitFillQty")) {
			order.scaleInitFillQty(changeInfo.getIntValue("scaleInitFillQty"));
		}
		if (changeInfo.containsKey("scaleRandomPercent")) {
			order.scaleRandomPercent(changeInfo.getBooleanValue("scaleRandomPercent"));
		}
		if (changeInfo.containsKey("scaleTable")) {
			order.scaleTable(changeInfo.getString("scaleTable"));
		}
	}

	public int orderId() { return order.orderId(); }
	public long permId() { return order.permId(); }
	public String account() { return order.account(); }
	public String orderRef() { return externalOrderRef(); }
	
	// Updated from AllOrderHandler.orderStatus();
	Double filled = 0.0, remaining = 0.0, avgFillPrice = 0.0, lastFillPrice = 0.0, mktCapPrice = 0.0;
	String whyHeld;
	boolean statusFilled = false; // Basic status need to be filled before using this order.
	public void setStatus(
			int _orderId, OrderStatus _status, Decimal _filled,
			Decimal _remaining, double _avgFillPrice,
			long _permId, int _parentId, double _lastFillPrice,
			int _clientId, String _whyHeld, double _mktCapPrice) {
		if(_orderId != order.orderId())
			err("setStatus() orderId not coinsistent " + _orderId + "," + order.orderId());
		setStatus(_status, _filled, _remaining, _avgFillPrice, _permId, _parentId, _lastFillPrice, _clientId, _whyHeld, _mktCapPrice);
	}
	public void setStatus(
			OrderStatus _status, Decimal _filled,
			Decimal _remaining, double _avgFillPrice,
			long _permId, int _parentId, double _lastFillPrice,
			int _clientId, String _whyHeld, double _mktCapPrice) {
		if(order.permId() != 0 && _permId != order.permId())
			err("setStatus() permId not coinsistent " + _permId + "," + order.permId());
		if (order.totalQuantity().longValue() != _filled.longValue() + _remaining.longValue())
			err("setStatus() size not coinsistent (" + _filled + "+" + _remaining + "), "+ order.totalQuantity().toString());
		orderState.status(_status);
		if (Math.abs(remaining) < Math.abs(_remaining.longValue()))
			remaining = (double) _remaining.longValue();
		if (Math.abs(filled) < Math.abs(_filled.longValue()))
			filled = (double) _filled.longValue();
		order.filledQuantity(Decimal.get(_filled.longValue()));
		if (order.permId() == 0)
			order.permId(_permId);
		else if (order.permId() != _permId)
			systemAbort("Inconsistent permId " + order.permId() + " - " + _permId);
		if (order.parentId() == 0)
			order.parentId(_parentId);
		else if (order.parentId() != _parentId)
			systemAbort("Inconsistent _parentId " + order.parentId() + " - " + _parentId);
		if (Math.abs(avgFillPrice) < Math.abs(_avgFillPrice))
			avgFillPrice = _avgFillPrice;
		if (Math.abs(lastFillPrice) < Math.abs(_lastFillPrice))
			lastFillPrice = _lastFillPrice;
		if (Math.abs(mktCapPrice) < Math.abs(_mktCapPrice))
			mktCapPrice = _mktCapPrice;
		whyHeld = _whyHeld;
		statusFilled = true;
		fixIBBug01();
		log("<-- orderStatus:\n" + toString());
	}

	public void orderState(OrderState os) { orderState = os; }
	
	// Additional status extension, for AllOrderHandler.handle(msg).
	private String extStatus = null;
	private String extMsg = null;
	public void setRejected (String msg) {
		extStatus = "Rejected";
		extMsg = msg;
	}
	public void setCancelled (String msg) {
		extStatus = "Cancelled";
		extMsg = msg;
	}
	public void setCompleted() { statusFilled = true; }
	/**
	 * return null : unknown
	 */
	public Boolean isAlive() {
		if (statusFilled != true) return null;
		if (extStatus != null) {
			switch (extStatus) {
			case "Rejected":
				return false;
			case "Cancelled":
				return false;
			default:
				errWithTrace("Unknwon extStatus " + extStatus);
				break;
			}
		}
		if (orderState == null) return null;
		if (orderState.status().isActive()) return true;
		if (orderState.status() == OrderStatus.Inactive) {
			// https://interactivebrokers.github.io/tws-api/order_submission.html#order_status
			if (order.orderRef() != null && order.orderRef().startsWith("uranus_"))
				return true; // an order is placed manually in TWS while the exchange is closed.
			else
				return false;
		}
		if (orderState.status() == OrderStatus.Unknown) return true;
		return false;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(StringUtils.rightPad(contract.shownName(), 40));
		
		// BUY Price
		sb.append(StringUtils.rightPad(order.action().toString(), 5));
		sb.append(StringUtils.rightPad(""+order.lmtPrice(), 8));

		// execute/total
		if (statusFilled)
			sb.append(StringUtils.leftPad("" + order.filledQuantity().toString(), 8));
		else
			sb.append(StringUtils.leftPad("??", 8));
		sb.append('/');
		sb.append(StringUtils.rightPad("" + order.totalQuantity().toString(), 8));
		
		if (extStatus != null)
			sb.append(StringUtils.rightPad(extStatus, 16));
		else if (orderState != null)
			sb.append(StringUtils.rightPad(orderState.status().toString(), 16));
		else
			sb.append(StringUtils.rightPad("---", 16));

		sb.append(StringUtils.rightPad("" + order.permId(), 12));
		sb.append(StringUtils.rightPad("[" + order.orderId() + "]", 6));

		if (extMsg != null) {
			sb.append('\n');
			sb.append(extMsg);
		}
		
		if (order.action() == Action.BUY)
			return green(sb.toString());
		if (order.action() == Action.SELL)
			return red(sb.toString());
		return sb.toString();
	}

	/**
	 * Compatiable with URANUS OMS system.
	 */
	public JSONObject toOMSJSON() {
		if (!statusFilled)
			errWithTrace("Should not call toOMSJSON() when statusFilled is false");
		else if (!contract.isFullDetailed())
			errWithTrace("Should not call toOMSJSON() when contract has no full detail");
		JSONObject j = new JSONObject();
		
		j.put("permId", ""+order.permId());
		String publishedRef = externalOrderRef();
		if (!isChildOrder() || publishedRef != null)
			j.put("orderRef", publishedRef);
		
		j.put("client_oid", omsClientOID());
		String omsId = omsId();
		if (omsId != null)
			j.put("i", omsId);

		if (isChildOrder()) {
			if (order.parentId() != 0)
				j.put("parentId", order.parentId());
			if (order.parentPermId() != 0)
				j.put("parentPermId", "" + order.parentPermId());
		}
		
		j.put("pair", contract.pair());
		if (order.action() == Action.BUY)
			j.put("T", "buy");
		else if (order.action() == Action.SELL)
			j.put("T", "sell");
		else
			errWithTrace("Unknown order action " + order.action());
		j.put("ttl_qty", order.totalQuantity().longValue());
		j.put("p", order.lmtPrice());
		if (avgFillPrice == 0)
			j.put("avg_price", order.lmtPrice());
		else
			j.put("avg_price", avgFillPrice);
		long execQty = 0;
		if (order.filledQuantity().longValue() != Long.MAX_VALUE) {
			execQty = order.filledQuantity().longValue();
		}
		j.put("executed_qty", execQty);
		j.put("remained_qty", order.totalQuantity().longValue()-execQty);
		j.put("status", extStatus == null ? orderState.status().toString() : extStatus);
		// created time missing, default 2000-01-01 00:00:00
		// suggest using orderRef to store client_oid+timestamp when created.
		j.put("t", 946656000000l);
		j.put("updateTime", System.currentTimeMillis());
		j.put("market", contract.exchange());
		j.put("orderType", order.orderType()); // LMT
		j.put("tif", order.tif());
		j.put("whatIf", order.whatIf());
		j.put("secType", contract.secType());
		if (orderState.commissionAndFees() == Double.MAX_VALUE) {
			j.put("commission", 0);
		}
		else
			j.put("commission", orderState.commissionAndFees());

		j.put("extMsg", extMsg);

		// ScaleTrader fields
		if (order.scaleInitLevelSize() != Integer.MAX_VALUE)
			j.put("scaleInitLevelSize", order.scaleInitLevelSize());
		if (order.scaleSubsLevelSize() != Integer.MAX_VALUE)
			j.put("scaleSubsLevelSize", order.scaleSubsLevelSize());
		if (order.scalePriceIncrement() != Double.MAX_VALUE)
			j.put("scalePriceIncrement", order.scalePriceIncrement());
		if (order.scalePriceAdjustValue() != Double.MAX_VALUE)
			j.put("scalePriceAdjustValue", order.scalePriceAdjustValue());
		if (order.scalePriceAdjustInterval() != Integer.MAX_VALUE)
			j.put("scalePriceAdjustInterval", order.scalePriceAdjustInterval());
		if (order.scaleProfitOffset() != Double.MAX_VALUE)
			j.put("scaleProfitOffset", order.scaleProfitOffset());
		if (order.scaleAutoReset())
			j.put("scaleAutoReset", true);
		if (order.scaleInitPosition() != Integer.MAX_VALUE)
			j.put("scaleInitPosition", order.scaleInitPosition());
		if (order.scaleInitFillQty() != Integer.MAX_VALUE)
			j.put("scaleInitFillQty", order.scaleInitFillQty());
		if (order.scaleRandomPercent())
			j.put("scaleRandomPercent", true);
		if (order.scaleTable() != null && !order.scaleTable().isEmpty())
			j.put("scaleTable", order.scaleTable());

		return j;
	}

	public String omsId() {
		if (omsClientOID() != null) {
			// suggest using orderRef to store client_oid+timestamp when created with API
			return omsClientOID();
		} else if (omsAltId() != null){
			// For those non-API orders.
			return omsAltId();
		} else if (isChildOrder() && order.permId() == 0) {
			return null;
		} else {
			err("omsId not found, is this order placed mannually from TWS?\n" + this);
			return null;
		}
	}
	
	public String omsAltId() {
		// If order has customized api compatiable ID, don't return.
		if (omsClientOID() != null) return null;
		// alternative ID is permId, if assigned.
		if (order.permId() == 0) return null;
		return ""+order.permId();
	}
	public String omsClientOID() {
		// suggest using orderRef to store client_oid+timestamp when created with API
		String orderRef = externalOrderRef();
		if (orderRef != null && orderRef.startsWith("uranus_")) return orderRef;
		if (orderRef != null && orderRef.startsWith("api_")) return orderRef;
		return null;
	}
}
