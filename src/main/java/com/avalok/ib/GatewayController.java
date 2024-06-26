package com.avalok.ib;

import static com.bitex.util.DebugUtil.*;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.text.SimpleDateFormat;
import java.util.*;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.avalok.ib.controller.BaseIBController;
import com.avalok.ib.handler.*;
import com.bitex.util.Redis;

import com.ib.client.*;
import com.ib.client.Types.*;
import com.ib.controller.AccountSummaryTag;
import com.ib.controller.ApiController;
import com.ib.controller.ApiController.IHistoricalDataHandler;
import com.ib.controller.ApiController.ITopMktDataHandler;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

import java.util.ArrayList;

public class GatewayController extends BaseIBController {
	public static void main(String[] args) throws Exception {
		if (Redis.connectivityTest() == false)
			systemAbort("Seems redis does not work properlly");
		new GatewayController().listenCommand();
	}

	private final String ackChannel;
	public GatewayController() {
		ContractDetailsHandler.GW_CONTROLLER = this;
		MarketRuleHandler.GW_CONTROLLER = this;
//		AccountMVHandler.GW_CONTROLLER = this;

		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				orderCacheHandler.teardownOMS("Shutting down");
			}
		});
		// Keep updating working status '[true, timestamp]' in 
		// Redis/IBGateway:{name}:status every second.
		// If this status goes wrong, all other data could not be trusted.
		long liveStatusInvertal = 1000;
		final String liveStatusKey = "IBGateway:" + _name + ":status";
		ackChannel = "IBGateway:"+_name+":ACK";
		new Timer("GatewayControllerLiveStatusWriter").scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				JSONObject j = new JSONObject();
				j.put("type", "heartbeat");
				j.put("status", isConnected());
				j.put("t", System.currentTimeMillis());
				Redis.set(liveStatusKey, j);
				Redis.pub(ackChannel, j);
			}
		}, 0, liveStatusInvertal);
	}

	////////////////////////////////////////////////////////////////
	// Market depth data module, ridiculous limitation here:
	// Max number (3) of market depth requests has been reached
	////////////////////////////////////////////////////////////////
	private ConcurrentHashMap<String, DeepMktDataHandler> _depthTasks = new ConcurrentHashMap<>();
	private ConcurrentHashMap<Integer, String> _depthTaskByReqID = new ConcurrentHashMap<>();
	private final boolean isSmartDepth = false;

	private int subscribeDepthData(IBContract contract) {
		String jobKey = contract.exchange() + "/" + contract.shownName();
		if (_depthTasks.get(jobKey) != null) {
			err("Task dulicated, skip subscribing depth data " + jobKey);
			return 0;
		}
		log("Subscribe depth data for " + jobKey);
		int numOfRows = 10;
		DeepMktDataHandler handler = new DeepMktDataHandler(contract, true);
		_apiController.reqDeepMktData(contract, numOfRows, isSmartDepth, handler);
		int qid = _apiController.lastReqId();
		_depthTaskByReqID.put(qid, jobKey); // reference for error msg
		_depthTasks.put(jobKey, handler);
		return qid;
	}

	private int unsubscribeDepthData(IBContract contract) {
		String jobKey = contract.exchange() + "/" + contract.shownName();
		DeepMktDataHandler handler = _depthTasks.get(jobKey);
		if (_depthTasks.get(jobKey) == null) {
			err("Task not exist, skip canceling depth data " + jobKey);
			return 0;
		}
		log("Cancel depth data for " + jobKey);
		_apiController.cancelDeepMktData(isSmartDepth, handler);
		int qid = _apiController.lastReqId();
		_depthTasks.remove(jobKey);
		return qid;
	}
	
	////////////////////////////////////////////////////////////////
	// Market top data module
	////////////////////////////////////////////////////////////////

	private ConcurrentHashMap<String, TopMktDataHandler> _topTasks = new ConcurrentHashMap<>();
	private ConcurrentHashMap<String, OptionTopMktDataHandler> _optionTopTasks = new ConcurrentHashMap<>();
	private ConcurrentHashMap<Integer, String> _topTaskByReqID = new ConcurrentHashMap<>();
	private int subscribeTopData(IBContract contract) {
		String jobKey = contract.exchange() + "/" + contract.shownName();
		boolean isOptType = contract.secType() == SecType.OPT;
		if (isOptType && _optionTopTasks.get(jobKey) != null) {
			log("Task dulicated, skip subscribing option top data " + jobKey);
			return 0;
		} else if(!isOptType && _topTasks.get(jobKey) != null) {
			log("Task dulicated, skip subscribing top data " + jobKey);
			return 0;
		}

		if (isOptType){
			log("Subscribe option top data for " + jobKey);
			boolean broadcastTop = true, broadcastTick = true;
			OptionTopMktDataHandler handler = new OptionTopMktDataHandler(contract, broadcastTop, broadcastTick);
			String genericTickList = "";

			// Request snapshot, then updates
			boolean snapshot = true, regulatorySnapshot = false;
			_apiController.reqOptionMktData(contract, genericTickList, snapshot, regulatorySnapshot, handler);
			snapshot = false; regulatorySnapshot = false;
			_apiController.reqOptionMktData(contract, genericTickList, snapshot, regulatorySnapshot, handler);
			_optionTopTasks.put(jobKey, handler);
		} else {
			log("Subscribe top data for " + jobKey);
			boolean broadcastTop = true, broadcastTick = true;
			TopMktDataHandler handler = new TopMktDataHandler(contract, broadcastTop, broadcastTick);
			// See <Generic tick required> at https://interactivebrokers.github.io/tws-api/tick_types.html
			String genericTickList = "";

			// Request snapshot, then updates
			boolean snapshot = true, regulatorySnapshot = false;
			_apiController.reqTopMktData(contract, genericTickList, snapshot, regulatorySnapshot, handler);
			snapshot = false; regulatorySnapshot = false;
			_apiController.reqTopMktData(contract, genericTickList, snapshot, regulatorySnapshot, handler);
			_topTasks.put(jobKey, handler);
		}
		int qid = _apiController.lastReqId();
		_topTaskByReqID.put(qid, jobKey); // reference for error msg
		return qid;
	}

	private int unsubscribeTopData(IBContract contract) {
		String jobKey = contract.exchange() + "/" + contract.shownName();
		boolean isOptType = contract.secType() == SecType.OPT;
		if (isOptType && _optionTopTasks.get(jobKey) == null) {
			err("Task not exist, skip canceling option top data " + jobKey);
			return 0;
		} else if (!isOptType && _topTasks.get(jobKey) == null) {
			err("Task not exist, skip canceling top data " + jobKey);
			return 0;
		}

		if (isOptType){
			OptionTopMktDataHandler optHandler = _optionTopTasks.get(jobKey);
			log("Cancel option top data for " + jobKey);
			_apiController.cancelTopMktData(optHandler);
			_optionTopTasks.remove(jobKey);
		} else {
			TopMktDataHandler handler = _topTasks.get(jobKey);
			log("Cancel top data for " + jobKey);
			_apiController.cancelTopMktData(handler);
			_topTasks.remove(jobKey);
		}
		int qid = _apiController.lastReqId();
		return qid;
	}

	private void restartMarketData() {
		info("Re-subscribe all depth data");
		DeepMktDataHandler[] handlers1 = _depthTasks.values().toArray(new DeepMktDataHandler[0]);
		for (DeepMktDataHandler h : handlers1) {
			unsubscribeDepthData(h.contract());
			subscribeDepthData(h.contract());
		}
		info("Re-subscribe all top data");
		TopMktDataHandler[] handlers2 = _topTasks.values().toArray(new TopMktDataHandler[0]);
		for (TopMktDataHandler h : handlers2) {
			unsubscribeTopData(h.contract());
			subscribeTopData(h.contract());
		}
		info("Re-subscribe all option top data");
		OptionTopMktDataHandler[] handlers3 = _optionTopTasks.values().toArray(new OptionTopMktDataHandler[0]);
		for (OptionTopMktDataHandler h : handlers3) {
			unsubscribeTopData(h.contract());
			subscribeTopData(h.contract());
		}
	}
	
//	public ITopMktDataHandler findMktDataHandler(IBContract contract) {
//		String jobKey = contract.exchange() + "/" + contract.shownName();
//		log("jobKey " + jobKey);
//		if (contract.secType() == SecType.OPT) {
//			log("opt keys: "+ new ArrayList<>(_optionTopTasks.keySet()));
//			return _optionTopTasks.get(jobKey);
//		}
//		log("top keys: "+ new ArrayList<>(_topTasks.keySet()));
//		return _topTasks.get(jobKey);
//	}
	
	////////////////////////////////////////////////////////////////
	// Account balance
	////////////////////////////////////////////////////////////////
	// Use AccountMVHandler instead, PositionHandler does not have CASH balance.
//	protected PositionHandler posHandler = new PositionHandler();
//	protected void subscribeAccountPosition() { // TODO Is this streaming updating?
//		_apiController.reqPositionsMulti("", "", posHandler);
//	}
	
	protected AccountMVHandler accountMVHandler = new AccountMVHandler();
	public void subscribeAccountMV() { // Is this streaming updating? Yes, with some latency 1~5s.
		boolean subscribe = true;
		log("--> Req account mv default");
		if (accList != null) {
			for (String account : accList) {
				log("--> Req account mv " + account);
				_apiController.reqAccountUpdates(subscribe, account, accountMVHandler);
//				-----------------------------------------------------------------------------------------
//				- https://interactivebrokers.github.io/tws-api/account_updates.html
//				-----------------------------------------------------------------------------------------
//				only one account at a time can be subscribed at a time. Attempting a second 
//				subscription without previously cancelling an active one will not yield any 
//				error message although it will override the already subscribed account with the new one. 
//				-----------------------------------------------------------------------------------------
				sleep(1000);
//				_apiController.reqAccountUpdates(false, account, accountMVHandler);
			}
		} else {
			log("--> Req account mv default");
			_apiController.reqAccountUpdates(subscribe, "", accountMVHandler);
		}
	}

	protected String subscribeAccountStr;
	public void changeSubscribeAccountMV(String ac) {
		if (subscribeAccountStr != ac) {
			subscribeAccountStr = ac;
			_apiController.reqAccountUpdates(true, subscribeAccountStr, accountMVHandler);
		}
	}
	
	////////////////////////////////////////////////////////////////
	// Account Summary
	////////////////////////////////////////////////////////////////
	protected AccountSummaryHandler accountSummaryHandler = new AccountSummaryHandler();

	protected int queryAccountSummary() {
		if (!isConnected()) {
			return 0;
		}
		AccountSummaryTag[] a = AccountSummaryTag.values();
		_apiController.cancelAccountSummary(accountSummaryHandler);
		_apiController.reqAccountSummary("All", AccountSummaryTag.values(), accountSummaryHandler);
		return _apiController.lastReqId();
	}
	
	////////////////////////////////////////////////////////////////
	// Order & trades updates.
	////////////////////////////////////////////////////////////////
	protected AllOrderHandler orderCacheHandler = new AllOrderHandler(this);
	protected void subscribeTradeReport() {
		_apiController.reqExecutions(new ExecutionFilter(), orderCacheHandler);
	}
	protected void refreshLiveOrders() {
		_apiController.takeFutureTwsOrders(orderCacheHandler);
		_apiController.takeTwsOrders(orderCacheHandler);
		_apiController.reqLiveOrders(orderCacheHandler);
	}
	protected void refreshCompletedOrders() {
		_apiController.reqCompletedOrders(orderCacheHandler);
	}
	
	////////////////////////////////////////////////////////////////
	// Order actions
	////////////////////////////////////////////////////////////////
	protected int placeOrder(IBOrder order) throws Exception {
		if (order.omsClientOID() == null)
			throw new Exception("Abort placing order without OMS client_oid, no orderRef?");
		_apiController.placeOrModifyOrder(order.contract, order.order, new SingleOrderHandler(this, orderCacheHandler, order));
		return _apiController.lastReqId();
	}
	protected int cancelOrder(String omsId) {
		IBOrder order = orderCacheHandler.orderByOMSId(omsId);
		if (order == null) {
			err("Abort order cancelling, no order by oms id " + omsId);
			return 0;
		} else if (order.orderId() == 0) {
			err("Abort order cancelling, invalid order id 0 by omsId " + omsId + " refreshing orders now");
			// Might because some order updates is not received.
			refreshLiveOrders();
			return 0;
		}
		log("Find order by oms id " + omsId + " cancel " + order.orderId() + "\n" + order.toString());
		_apiController.cancelOrder(order.orderId());
		return _apiController.lastReqId();
	}
	protected int cancelAll() {
		_apiController.cancelAllOrders();
		return _apiController.lastReqId();
	}
	
	////////////////////////////////////////////////////////////////
	// Contract details query.
	////////////////////////////////////////////////////////////////
	public int queryContractListWithCache(JSONObject contractWithLimitInfo) {
		IBContract contract = new IBContract(contractWithLimitInfo);
		// Query this in cache, would trigger reqContractDetails() if cache missed.
		JSONObject details = ContractDetailsHandler.findDetails(contract);
		if (details != null) // Cache hit.
			return 0;
		return _apiController.lastReqId();
	}
	public int queryContractList(IBContract ibc) {
		_apiController.reqContractDetails(ibc, ContractDetailsHandler.instance);
		return _apiController.lastReqId();
	}
	public int queryMarketRule(int ruleId) {
		_apiController.reqMarketRule(ruleId, MarketRuleHandler.instance);
		return _apiController.lastReqId();
	}

	public int queryContractListToRedis(JSONObject contractWithLimitInfo, Long id) {
		IBContract ibc = new IBContract(contractWithLimitInfo);
		_apiController.reqContractDetailsToRedis(ibc, ContractDetailsHandler.instance, id);
		return _apiController.lastReqId();
	}
	////////////////////////////////////////////////////////////////
	// History data
	////////////////////////////////////////////////////////////////
	protected int queryHistoryDataToRedis(JSONObject j, Long id) {
		String endDateTime = j.getString("endDateTime");
		Integer duration = j.getInteger("duration");
		IBContract contract = new IBContract(j.getJSONObject("contract"));

		HistoricalDataHandler handler = new HistoricalDataHandler(id);
		_apiController.reqHistoricalData(contract, endDateTime, duration, DurationUnit.DAY, BarSize._1_day, WhatToShow.TRADES, false, false, handler);
		return _apiController.lastReqId();
	}

	////////////////////////////////////////////////////////////////
	// Life cycle and command processing
	////////////////////////////////////////////////////////////////
	@Override
	protected void _postConnected() {
		log("_postConnected");
		// Reset every cache status.
		// Contract detail cache does not need to be reset, always not changed.
		orderCacheHandler.resetStatus();
		
		// Then subscribe market data.
		subscribeTradeReport();
		restartMarketData();

		log("_postConnected delay 3 seconds to subscribe MV and refresh orders");
		// To have enough contract data to replace 'SMART' exchange
		// Delay to subscribe order snapshot
		new Timer("GatewayControllerDelayTask _postConnected()").schedule(new TimerTask() {
			@Override
			public void run() {
				while (true) {
					if (isConnected() && accList != null) {
						subscribeAccountMV();
						log("_postConnected : refresh alive and completed orders");
						refreshLiveOrders();
						refreshCompletedOrders();
						break;
					} else {
						log("_postConnected : isConnected " + isConnected() + " accList null? " + (accList != null));
						sleep(1000);
					}
				}
			}
		}, 3000);
	}

	@Override
	protected void _postDisconnected() {
		log("_postDisconnected");
		orderCacheHandler.teardownOMS("_postDisconnected()");
		orderCacheHandler.resetStatus();
	}

	private JedisPubSub commandProcessJedisPubSub = new JedisPubSub() {
		public void onMessage(String channel, String message) {
			JSONObject j = null;
			try {
				j = JSON.parseObject(message);
			} catch (Exception e) {
				err("<<< CMD " + message);
				err("Failed to parse command " + e.getMessage());
				return;
			}
			log("test: _twsConnected: "+ _twsConnected + ", _apiConnected: "+ _apiConnected);
			final Long id = j.getLong("id");
			info("<<< CMD " + id + " " + j.getString("cmd"));
			String errorMsg = null;
			String response = null; // Some commands could have response directly.
			int apiReqId = 0;
			try {
				switch(j.getString("cmd")) {
				case "SUB_ODBK":
					apiReqId = subscribeDepthData(new IBContract(j.getJSONObject("contract")));
					break;
				case "UNSUB_ODBK":
					apiReqId = unsubscribeDepthData(new IBContract(j.getJSONObject("contract")));
					break;
				case "SUB_TOP":
					apiReqId = subscribeTopData(new IBContract(j.getJSONObject("contract")));
					break;
				case "UNSUB_TOP":
					apiReqId = unsubscribeTopData(new IBContract(j.getJSONObject("contract")));
					break;
				case "RESET":
					_postConnected();
					break;
				case "FIND_CONTRACTS":
					apiReqId = queryContractListWithCache(j.getJSONObject("contract"));
					break;
				case "FIND_CONTRACTS_TO_REDIS":
					apiReqId = queryContractListToRedis(j.getJSONObject("contract"), id);
					break;
				case "PLACE_ORDER":
					apiReqId = placeOrder(new IBOrder(j.getJSONObject("iborder")));
					break;
				case "CANCEL_ORDER":
					apiReqId = cancelOrder(j.getString("omsId"));
					break;
				case "CANCEL_ALL":
					apiReqId = cancelAll();
					break;
				case "ACCOUNT_LIST":
					response = JSON.toJSONString(accList);
					break;
				case "UPDATE_ACCOUNT_MV":
					subscribeAccountMV();
					break;
				case "FIND_ACCOUNT_SUMMARY":
					apiReqId = queryAccountSummary();
					break;
				case "FIND_HISTORY":
//					Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));
//					cal.add(Calendar.MONTH, -1);
//					SimpleDateFormat form = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
//					String formatted = form.format(cal.getTime());
//					20031126 15:59:00 US/Eastern
//					formatted: 20230714
//					20230714 11:08:49
//					20230814 23:59:59 Asia/Hong_Kong
////					log("formatted: " + formatted);
//					String endDateTime = j.getString("endDateTime");
//					log("endDateTime: " + endDateTime);
//					IBContract contract = new IBContract(j.getJSONObject("contract"));
//					HistoricalDataHandler ss = new HistoricalDataHandler();
////    				(Contract contract, String endDateTime, int duration, DurationUnit durationUnit, BarSize barSize, WhatToShow whatToShow, boolean rthOnly, boolean keepUpToDate, IHistoricalDataHandler handler)
//					_apiController.reqHistoricalData(contract, endDateTime, 3, DurationUnit.DAY, BarSize._1_day, WhatToShow.TRADES, false, false, ss);
					apiReqId = queryHistoryDataToRedis(j, id);
					break;
				case "REQ_EXECUTIONS":
					_apiController.reqExecutions(new ExecutionFilter(), new TradeReportHandler());
					break;
				case "FIND_ORDER_PICK_CONTRACT":
					IBOrder _ibOrder = new IBOrder(j.getJSONObject("contract"));
//					log(new IBOrder(j.getJSONObject("data")).cloneWithRealExchange());
					response = JSON.toJSONString(_ibOrder.cloneWithRealExchange().contract.toJSON());
					break;

//				case "UPDATE_OPT_GREEKS":
////					_apiController.reqAccountUpdates(true, "All", accountMVHandler);
//					for (IBContract c : accountMVHandler.ibc_cache.values()) {
//						String jobKey = c.exchange() + "/" + c.shownName();
//
//						if (c.secType() == SecType.CASH) {
//							continue;
//						}
//						else if (c.secType() == SecType.OPT) {
//							OptionTopMktDataHandler optHandler = _optionTopTasks.get(jobKey);
//							if (optHandler == null) {
//								optHandler = new OptionTopMktDataHandler(c, true, true);
//								_optionTopTasks.put(jobKey, optHandler);
//							}
////							_apiController.reqOptionVolatility(c, 0, 0, optHandler);
////							_apiController.reqTopMktData(c, "", true , false, optHandler);
//							_apiController.reqOptionMktData(c, "", true , false, optHandler);
//						} else {
//							TopMktDataHandler handler = _topTasks.get(jobKey);
//							if (handler == null) {
//								handler = new TopMktDataHandler(c, true, true);
//								_topTasks.put(jobKey, handler);
//							}
//							_apiController.reqTopMktData(c, "", true , false, handler);
//						}
//					}
//					break;
				default:
					errorMsg = "Unknown cmd " + j.getString("cmd");
					err(errorMsg);
					break;
				}
			} catch (Exception e) {
				errorMsg = e.getMessage();
				e.printStackTrace();
			} finally { // Reply with id in boradcasting.
				info(">>> ACK " + id + " ibApiId " + apiReqId);
				JSONObject r = new JSONObject();
				r.put("type", "ack");
				r.put("reqId", id);
				r.put("ibApiId", apiReqId);
				if (errorMsg != null)
					r.put("err", errorMsg);
				if (response != null)
					r.put("res", response);
				Redis.pub(ackChannel, r);
			}
		}
	};

	private void listenCommand() throws Exception {
		while (true) {
			Redis.exec(new Consumer<Jedis>() {
				@Override
				public void accept(Jedis t) {
					String cmdChannel = "IBGateway:"+_name+":CMD";
					info("Command listening started at " + cmdChannel);
					t.subscribe(commandProcessJedisPubSub, cmdChannel);
				}
			});
			err("Restart command listening in 1 second");
			sleep(1000);
		}
	}

	////////////////////////////////////////////////////////////////
	// TWS Message processing
	////////////////////////////////////////////////////////////////
	@Override
	public void message(int id, int errorCode, String errorMsg) {
		log("id:" + id + ", code:" + errorCode + ", msg:" + errorMsg);
		JSONObject j = new JSONObject();
		j.put("type", "msg");
		j.put("ibApiId", id);
		j.put("code", errorCode);
		j.put("msg", errorMsg);
		switch (errorCode) {
		case 200: // No security/exchange has been found for the request
			Redis.pub(ackChannel, j);
			break;
		case 309: // Max number (3) of market depth requests has been reached
			String depthJobKey = _depthTaskByReqID.remove(id);
			if (depthJobKey != null) {
				err("Remove failed depth task " + depthJobKey);
				_depthTasks.remove(depthJobKey);
				j.put("type", "error");
				j.put("msg", "depth req failed " + depthJobKey);
				j.put("detail", depthJobKey + " " + errorMsg);
				Redis.pub(ackChannel, j);
				break;
			}
			Redis.pub(ackChannel, j);
			break;
		case 317: // Market depth data has been RESET. Please empty deep book contents before applying any new entries.
			log("Initialise all data jobs again.");
			_postConnected();
			break;
		case 2103: // Market data farm connection is broken
			log("Initialise all data jobs again.");
			_postConnected();
			break;
		case 2105: // HMDS data farm connection is broken
			log("Initialise all data jobs again.");
			_postConnected();
			break;
		case 2108: // Market data farm connection is inactive but should be available upon demand
			log("Initialise all data jobs again.");
			_postConnected();
			break;
		case 2157: // msg:Sec-def data farm connection is broken:secdefhk
			break;
		case 162:
			j.put("type", "error");
			j.put("msg", errorMsg);
			Redis.pub(ackChannel, j);
			warn("Unhandled Message: id:" + id + ", code:" + errorCode + ", msg:" + errorMsg);
			break;
		default:
			super.message(id, errorCode, errorMsg);
			if (super.latestMsgIsOkay == false)
				Redis.pub(ackChannel, j);
			break;
		}
	}

	public void ack(JSONObject j) {
		Redis.pub(ackChannel, j);
	}
}
