package com.avalok.ib;

import static com.bitex.util.DebugUtil.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.avalok.ib.controller.BaseIBController;
import com.avalok.ib.handler.*;
import com.bitex.util.Redis;

import com.ib.client.*;
import com.ib.client.Types.*;

import com.ib.controller.AccountSummaryTag;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

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
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				orderCacheHandler.teardownOMS("Shutting down");
//				teardownListenOrder();
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
			boolean snapshot = true, regulatorySnapshot = true;
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
			boolean snapshot = true, regulatorySnapshot = true;
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
			long delay = 3000L;
			for (String account : accList) {
				log("--> Req account mv " + account);
				_apiController.reqAccountUpdates(subscribe, account, accountMVHandler);
				sleep(delay);
			}
		} else {
			log("--> Req account mv default");
			_apiController.reqAccountUpdates(subscribe, "", accountMVHandler);
		}
	}

	public void reqAccountUpdates(JSONArray updateAcList) {
		long delay = 3000L;
		new Thread(() -> {
			for (Object account : updateAcList) {
				log("--> Req account mv " + account);
				_apiController.reqAccountUpdates(true, (String) account, accountMVHandler);
				sleep(delay);
			}
		}).start();
	}


	////////////////////////////////////////////////////////////////
	// Account Summary
	////////////////////////////////////////////////////////////////
	protected AccountSummaryHandler accountSummaryHandler = new AccountSummaryHandler();

	protected int queryAccountSummary() {
		if (!isConnected()) {
			return 0;
		}
//		AccountSummaryTag[] a = AccountSummaryTag.values();
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

//		SingleOrderHandler is implements IOrderCancelHandler
// 		use SingleOrderHandler to cancelOrder
		_apiController.cancelOrder(order.orderId(), new SingleOrderHandler(this, orderCacheHandler, order));
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
	// Redis control place/cancel order
	////////////////////////////////////////////////////////////////
//	private static Thread listenPlaceOrderQueueThread = new Thread();
//	private static Thread listenCancelOrderQueueThread = new Thread();
//	private final boolean TEST_REDIS_TRADE = false;
//	private List<String> placeOidCache = new ArrayList<>();
//
//	private void teardownListenOrder() {
//		if (listenPlaceOrderQueueThread.isAlive()) {
//			listenPlaceOrderQueueThread.interrupt();
//			err("Tear down listenPlaceOrderQueueThread");
//		}
//		if (listenCancelOrderQueueThread.isAlive()) {
//			listenCancelOrderQueueThread.interrupt();
//			err("Tear down listenCancelOrderQueueThread");
//		}
//	}
//
//	private void listenRedis() {
//		String placeOrderQueue = "test_place_order_queue";
//		String orderDataPool = "test_order_data_pool";
//		String placeOrderPassQueue = "test_place_order_pass_queue";
//
//		if (listenPlaceOrderQueueThread.isAlive()) {
//			listenPlaceOrderQueueThread.interrupt();
//		}
//		listenPlaceOrderQueueThread = new Thread(new Runnable() {
//			public void run() {
//				while (true) {
//					try {
//						if (Redis.connectivityTest() == false) {
//							sleep(1000);
//							continue;
//						}
//						Redis.exec(new Consumer<Jedis>() {
//							@Override
//							public void accept(Jedis r) {
////								log("place r.info(): " + r.info());
//								List<String> topDataList = r.brpop(60, placeOrderQueue);
//								if (topDataList == null || topDataList.size() == 0) {
//
//								} else {
//									String oid = topDataList.get(1);
//									IBOrder order = orderCacheHandler.orderByOMSId(oid);
//									if (order != null) {
//										err("orderId '" + oid + "' already exists");
//									} else {
//										String orderData = r.hget(orderDataPool, oid);
//										JSONObject j = null;
//										try {
//											j = JSON.parseObject(orderData);
//											JSONObject orderJson = j.getJSONObject("iborder");
//											IBOrder ibo = new IBOrder(orderJson);
//											Long orderTs = orderJson.getLongValue("timestamp");
//
//											if (System.currentTimeMillis() - 2000 > orderTs) {
//												log("test: order is oldest -> " + oid);
//												r.lpush(placeOrderPassQueue, oid);
//											} else {
//												placeOrder(ibo);
//												placeOidCache.add(oid);
//												log("test: place order -> " + oid);
//											}
//										} catch (Exception e) {
//											e.printStackTrace();
//											err("<<< CMD " + orderData);
//											err("Failed to parse command " + e.getMessage());
//										}
//									}
//								}
//							}
//						});
//					} catch (Exception e) {
//						err("Failed to listenPlaceOrderQueueThread " + e.getMessage());
//						return;
//					}
//
//				}
//			}
//		});
//		listenPlaceOrderQueueThread.start();
//
//		if (listenCancelOrderQueueThread.isAlive()) {
//			listenCancelOrderQueueThread.interrupt();
//		}
//		String cancelOrderQueue = "test_cancel_order_queue";
//		listenCancelOrderQueueThread = new Thread(new Runnable() {
//			public void run() {
//				while (true) {
//					try {
//						if (Redis.connectivityTest() == false) {
//							sleep(1000);
//							continue;
//						}
//						Redis.exec(new Consumer<Jedis>() {
//							@Override
//							public void accept(Jedis r) {
////								log("cancel r.info(): " + r.info());
//								List<String> topDataList = r.brpop(60, cancelOrderQueue);
//
//								if (topDataList == null || topDataList.size() == 0) {}
//								else {
//									String cancelId = topDataList.get(1);
//									//							Push back to the queue and delete again after successful cancellation
//									r.rpush(cancelOrderQueue, cancelId);
//
//									int apiReqId = cancelOrder(cancelId);
//									if (apiReqId == 0 && placeOidCache.contains(cancelId)) {
//										log("oid -> " + cancelId + " not submit to place order");
//									} else {
//										r.lrem(cancelOrderQueue, 60, cancelId);
//										log("test: call cancel -> " + cancelId + " apiReqId: " + apiReqId);
//									}
//								}
//							}
//						});
//					} catch (Exception e) {
//						err("Failed to listenCancelOrderQueueThread " + e.getMessage());
//						return;
//					}
//				}
//			}
//		});
//		listenCancelOrderQueueThread.start();
//	}
	////////////////////////////////////////////////////////////////
	// Life cycle and command processing
	////////////////////////////////////////////////////////////////

//	private  static Timer connectTimer = new Timer("GatewayControllerDelayTask _postConnected()");
	private static TimerTask connectTimerTask;
	private static int connectMark = 0;
	private int retryConnectCount = 0;

	@Override
	protected void _postConnected() {
		log("_postConnected");
		// Reset every cache status.
		// Contract detail cache does not need to be reset, always not changed.
		orderCacheHandler.resetStatus();
		
		// Then subscribe market data.
//		if (_apiController == null) _connect();
		subscribeTradeReport();
		restartMarketData();

		log("_postConnected delay 3 seconds to subscribe MV and refresh orders");

		if (connectTimerTask != null) {
			connectTimerTask.cancel();
			connectTimerTask = null;
		}
		connectMark = connectMark < 1000 ? connectMark + 1: 0;
		connectTimerTask = new TimerTask() {
			@Override
			public void run() {
				final int cacheMark = connectMark;
				while (true) {
					// Make sure only one connectTimerTask
					if (cacheMark != connectMark) {
						break;
					}
					if (isConnected() && accList != null) {
						subscribeAccountMV();
						log("_postConnected : refresh alive and completed orders");
						refreshLiveOrders();
						refreshCompletedOrders();
//						if (TEST_REDIS_TRADE) listenRedis();
						connectTimerTask = null;
						retryConnectCount = 0;
						break;
					} else {
						log("_postConnected : isConnected " + isConnected() + " accList null? " + (accList != null));
						if (retryConnectCount >= 60) {
							log("retryConnectCount >= 60, call _connect");
							_connect();
							retryConnectCount = 0;
						} else {
							retryConnectCount++;
							sleep(1000);
						}
					}
				}
			}
		};

		// To have enough contract data to replace 'SMART' exchange
		// Delay to subscribe order snapshot
		Timer connectTimer = new Timer("GatewayControllerDelayTask _postConnected()");
		connectTimer.schedule(connectTimerTask, 3000);
	}

	@Override
	protected void _postDisconnected() {
		log("_postDisconnected");
		orderCacheHandler.teardownOMS("_postDisconnected()");
		orderCacheHandler.resetStatus();
//		teardownListenOrder();
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
			try {
				if (!_twsConnected || !_apiConnected) _connect();
			} catch (Exception e) {
				err("Failed to connect " + e.getMessage());
			}
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
//					subscribeAccountMV();
					reqAccountUpdates(j.getJSONArray("updateAcList"));
					break;
				case "FIND_ACCOUNT_SUMMARY":
					apiReqId = queryAccountSummary();
					break;
				case "FIND_HISTORY":
					apiReqId = queryHistoryDataToRedis(j, id);
					break;
				case "FIND_ORDER_PICK_CONTRACT":
//					response = JSON.toJSONString((new IBOrder(j.getJSONObject("contract"))).cloneWithRealExchange().toOMSJSON());
					IBOrder _ibOrder = new IBOrder(j.getJSONObject("contract"));
					response = JSON.toJSONString(_ibOrder.cloneWithRealExchange().contract.toJSON());
					break;
				case "REQ_EXECUTIONS":
					_apiController.reqExecutions(new ExecutionFilter(), new TradeReportHandler());
					break;
				case "TEST_DISCONNECT":
					disconnected();
					break;
//				case "TEST_PNL":
//					_apiController.reqExecutions();
//				case "UPDATE_OPT_GREEKS":
//					info("Double.MAX_VALUE: " + Double.MAX_VALUE);
//					for (IBContract c : accountMVHandler.ibc_cache.values()) {
//						if (c.secType() != SecType.OPT) {
//							OptionTopMktDataHandler optHandler = new OptionTopMktDataHandler(c, false, false);
//							_apiController.reqOptionMktData(c, "", true , false, optHandler);
//						} else {
//							TopMktDataHandler handler = new TopMktDataHandler(c, false, false);
//							_apiController.reqTopMktData(c, "", true , false, handler);
//						}
////						break;
//					}
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
	public void message(int id, int errorCode, String errorMsg, String advancedOrderRejectJson) {
		log("id:" + id + ", code:" + errorCode + ", msg:" + errorMsg + ", advancedOrderRejectJson:"+ advancedOrderRejectJson);
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
		default:
			super.message(id, errorCode, errorMsg, advancedOrderRejectJson);
			if (super.latestMsgIsOkay == false)
				Redis.pub(ackChannel, j);
			break;
		}
	}

	public void ack(JSONObject j) {
		Redis.pub(ackChannel, j);
	}
}
