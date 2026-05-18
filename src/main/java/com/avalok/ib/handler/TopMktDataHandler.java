package com.avalok.ib.handler;

import java.util.function.Consumer;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.avalok.ib.IBContract;
import com.bitex.util.Redis;
import com.ib.client.Contract;
import com.ib.client.Decimal;
import com.ib.client.TickAttrib;
import com.ib.client.TickType;
import com.ib.controller.ApiController.ITopMktDataHandler;

import redis.clients.jedis.Jedis;

import static com.bitex.util.DebugUtil.*;

/**
 * Reuse the broadcast and internal cache structure from DeepMktDataHandler
 */
public class TopMktDataHandler implements ITopMktDataHandler{
	protected boolean _debug = false;
	public final int max_depth = 1;
	protected IBContract _contract;
	public final double multiplier;
	protected final long marketDataSizeMultiplier;
	protected final String publishODBKChannel; // Publish odbk to universal system
	protected final String publishTickChannel; // Publish odbk to universal system
	protected final String setexTickChannel;

	protected final JSONArray topDataSnapshot = new JSONArray();
	protected final JSONObject[] topAsks = new JSONObject[] {new JSONObject()};
	protected final JSONObject[] topBids = new JSONObject[] {new JSONObject()};
	protected boolean topDataInited = false; // Wait until all ASK/BID filled

	protected final JSONArray newTicksData = new JSONArray();
	protected final JSONArray newTicks = new JSONArray();
	protected final JSONArray cacheTicksData = new JSONArray();
	protected final JSONArray cacheTicks = new JSONArray();
	// Wait until tickSnapshotEnd(), This function suddenly does not work any more. 20200514
	// protected boolean tickDataInited = false;
	protected boolean tickDataInited = true;
	protected String cacheKey = "Unknown";
	private Integer nowMarketDataType = null;

	private Consumer<Jedis> broadcastTopLambda;
	private Consumer<Jedis> broadcastTickLambda;
	private Consumer<Jedis> cacheTickLambda;
	public TopMktDataHandler(IBContract contract, long sizeMultiplier, boolean broadcastTop, boolean broadcastTick) {
		_contract = contract;
		publishODBKChannel = "URANUS:"+contract.exchange()+":"+contract.pair()+":full_odbk_channel";
		publishTickChannel = "URANUS:"+contract.exchange()+":"+contract.pair()+":full_tick_channel";
		setexTickChannel = "URANUS:"+contract.exchange()+":"+contract.pair()+":tick:expire";
//		publishODBKChannel = "URANUS:"+contract.pair()+":full_odbk_channel";
//		publishTickChannel = "URANUS:"+contract.pair()+":full_tick_channel";
		marketDataSizeMultiplier = sizeMultiplier;

		if (contract.multiplier() == null)
			multiplier = 1;
		else
			multiplier = Double.parseDouble(contract.multiplier());
//		Long t0 = System.currentTimeMillis();
//		new Thread(() -> {
//            try {
//                while (true) {
//					JSONObject contractDetail = ContractDetailsHandler.findDetails(contract);
//					if (contractDetail != null) {
//						marketDataSizeMultiplier = contractDetail.getIntValue("suggestedSizeIncrement");
//						break;
//					}
//					log("wait for contract details " + publishODBKChannel);
//                    Thread.sleep(1000);
//                }
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }).start();

		// while (true) {
		// 	JSONObject contractDetail = ContractDetailsHandler.findDetails(contract);
		// 	if (contractDetail != null) {
		// 		marketDataSizeMultiplier = contractDetail.getIntValue("suggestedSizeIncrement");
		// 		break;
		// 	}

		// 	if (t0 < System.currentTimeMillis() - 2000) {
		// 		Contract c = contract;
		// 		c.exchange("SMART");
		// 		IBContract smartIbc = new IBContract(c);
		// 		JSONObject smartContractDetail = ContractDetailsHandler.findDetails(smartIbc);
		// 		if (smartContractDetail != null) {
		// 			info("WARNING!! FIX _contract.exchange FROM"+ _contract.exchange() + " to SMART");
		// 			_contract = smartIbc;
		// 			marketDataSizeMultiplier = smartContractDetail.getIntValue("suggestedSizeIncrement");
		// 			break;
		// 		}
		// 	}
		// 	log("wait for contract details " + publishODBKChannel);
		// 	sleep(1000);
		// }
		// Pre-build snapshot
		topDataSnapshot.add(topBids);
		topDataSnapshot.add(topAsks);
		topDataSnapshot.add(0); // Timestamp
		topDataSnapshot.add(null); // data delay ms
		// Pre-build broadcast lambda.
		if (broadcastTop) {
			broadcastTopLambda = new Consumer<Jedis> () {
				@Override
				public void accept(Jedis t) {
					topDataSnapshot.set(2, System.currentTimeMillis());
					topDataSnapshot.set(3, delayMs);
					// Dont do this when same depth handler is working.
					Long depthT = DeepMktDataHandler.CHANNEL_TIME.get(publishODBKChannel);
					if (depthT == null || depthT < System.currentTimeMillis() - 1000) {
						if (_debug)
							warn("Publish to " + publishODBKChannel);
						t.publish(publishODBKChannel, JSON.toJSONString(topDataSnapshot));
					} else if (_debug)
						warn("Dont publish to " + publishODBKChannel);
				}
			};
		}

		// Pre-build tick data.
		newTicks.add(new JSONObject());
		newTicksData.add(newTicks);
		newTicksData.add(0); // Timestamp
		newTicksData.add(null); // data delay ms

		cacheTicks.add(new JSONObject());
		cacheTicksData.add(cacheTicks);
		cacheTicksData.add(0); // Timestamp
		cacheTicksData.add(null); // data delay ms
		// Pre-build broadcast lambda.
		if (broadcastTop) {
			broadcastTickLambda = new Consumer<Jedis>() {
				@Override
				public void accept(Jedis t) {
					if (_debug)
						warn("Publish to " + publishTickChannel);
					t.publish(publishTickChannel, JSON.toJSONString(newTicksData));
//					t.setex(setexTickChannel,300, JSON.toJSONString(newTicksData));
				}
			};

			cacheTickLambda = new Consumer<Jedis>() {
				@Override
				public void accept(Jedis t) {
					if (_debug)
						warn("Setex to " + setexTickChannel);
					t.setex(setexTickChannel,86400, JSON.toJSONString(cacheTicksData));
				}
			};
		}
	}

	public IBContract contract() { return _contract; }

	Double bidPrice, askPrice; // To determine last trade side
	@Override
	public void tickPrice(TickType tickType, double price, TickAttrib attribs) {
		if (_debug)
			info(_contract.shownName() + " tickPrice() tickType " + tickType + " price " + price + " attribs " + attribs);
		switch (tickType) {
			case BID:
				bidPrice = price;
				topBids[0].put("p", price);
				// if (topBids[0].getDouble("s") == null) break;
				// if (tickDataInited) broadcastTop(false);
				break;
			case ASK:
				askPrice = price;
				topAsks[0].put("p", price);
				// if (topAsks[0].getDouble("s") == null) break;
				// if (tickDataInited) broadcastTop(false);
				break;
			case LAST:
				lastTickPrice = price;
				break;
			case OPEN:
				break;
			case CLOSE:
				break;
			case LOW:
				info(_contract.shownName() + " tickPrice() tickType " + tickType + " price " + price + " attribs " + attribs);
				break;
			case HIGH:
				info(_contract.shownName() + " tickPrice() tickType " + tickType + " price " + price + " attribs " + attribs);
				break;
			case HALTED:
				break;
			//	Bond
			case BID_YIELD:
				break;
			case ASK_YIELD:
				break;
			case LAST_YIELD:
				break;
			// Refer https://interactivebrokers.github.io/tws-api/market_data_type.html
			// If live data is available a request for delayed data would be ignored by TWS
			// DELAYED types
			case DELAYED_BID:
				bidPrice = price;
				topBids[0].put("p", price);
				// if (topBids[0].getDouble("s") == null) break;
				// if (tickDataInited) broadcastTop(false);
				break;
			case DELAYED_ASK:
				askPrice = price;
				topAsks[0].put("p", price);
				// if (topAsks[0].getDouble("s") == null) break;
				// if (tickDataInited) broadcastTop(false);
				break;
			case DELAYED_LAST:
				// if have market data subscription only type LAST, don't have type DELAYED_LAST
				lastTickPrice = price;
				// info(_contract.shownName() + " tickPrice() tickType " + tickType + " price " + price + " attribs " + attribs);
				break;
			case DELAYED_OPEN:
				break;
			case DELAYED_CLOSE:
				break;
			case DELAYED_LOW:
				break;
			case DELAYED_HIGH:
				break;
			case DELAYED_HALTED:
				break;
			default:
				info(_contract.shownName() + " tickPrice() tickType " + tickType + " price " + price + " attribs " + attribs);
				break;
		}
	}

	@Override
	public void tickSize(TickType tickType, Decimal size_in_lot) {
		Double size;
		if (_contract.exchange().equals("SEHK") || _contract.exchange().equals("HKFE")){
			size = size_in_lot.longValue() * 1.0;
		} else {
//			log("size_in_lot: " + size_in_lot.longValue() + " multiplier: "+ multiplier + " marketDataSizeMultiplier: "+ marketDataSizeMultiplier);
			size = size_in_lot.longValue() * multiplier * marketDataSizeMultiplier;
			if (size == 0) {
				size = size_in_lot.longValue() * multiplier;
			}
		}
		if (_debug)
			info(_contract.shownName() + " tickSize() tickType " + tickType + " size " + size);
		switch (tickType) {
			case BID_SIZE:
				topBids[0].put("s", size);
				if (topBids[0].getDouble("p") == null) break;
				if (tickDataInited) broadcastTop(false);
				break;
			case ASK_SIZE:
				topAsks[0].put("s", size);
				if (topAsks[0].getDouble("p") == null) break;
				if (tickDataInited) broadcastTop(false);
				break;
			case LAST_SIZE:
				lastTickSize = size;
				// recordLastTrade();
				break;
			case VOLUME:
				lastTickVolume = size;
				cacheLastTrade();
				break;
			case OPEN:
				break;
			case CLOSE:
				break;
			case HALTED:
				break;
			// Refer https://interactivebrokers.github.io/tws-api/market_data_type.html
			// If live data is available a request for delayed data would be ignored by TWS
			// DELAYED types
			case DELAYED_BID_SIZE:
				topBids[0].put("s", size);
				if (topBids[0].getDouble("p") == null) break;
				if (tickDataInited) broadcastTop(false);
				break;
			case DELAYED_ASK_SIZE:
				topAsks[0].put("s", size);
				if (topAsks[0].getDouble("p") == null) break;
				if (tickDataInited) broadcastTop(false);
				break;
			case DELAYED_LAST_SIZE:
				// if have market data subscription only type LAST, don't have type DELAYED_LAST_SIZE
				lastTickSize = size;
				// recordLastTrade();
				// info(_contract.shownName() + " tickSize() tickType " + tickType + " size " + size);
				break;
			case DELAYED_VOLUME:
				lastTickVolume = size;
				cacheLastTrade();
				break;
			case DELAYED_HALTED:
				break;
			default:
				info(_contract.shownName() + " tickSize() tickType " + tickType + " size " + size);
				break;
		}
	}

	/////////////////////////////////////////////////////
	// Trade tick always comes with triple(or more) messages:
	// tickString() tickType LAST_TIMESTAMP VALUE: 1620015539
	// tickPrice() tickType LAST price 58280.0
	// tickSize() tickType LAST_SIZE size 1
	// tickSize() tickType LAST_SIZE size 2 (same price, multiple trades)
	// tickPrice() tickType LAST price 59120.0 (if have more trade at same time)
	// tickSize() tickType LAST_SIZE size 1 (if have more trade at same time)
	/////////////////////////////////////////////////////
	private Long lastTickTime = 0l;
	public Double lastTickPrice = null;
	private Double lastTickSize = null;
	private Double lastTickVolume = 0.0;
	private JSONObject lastTrade;
	private JSONObject cacheTrade;
	private Long delayMs = null;

	private void recordLastTrade() {
		if (tickDataInited == false) return;
		if (lastTickSize == 0) return;
		if (lastTickPrice == null || lastTickPrice <= 0 || lastTickSize == null || lastTickSize < 0) {
			err(_contract.shownName() + " Call recordLastTrade() with incompleted data " + _contract.shownName() + " lastTickPrice "
					+ lastTickPrice + " lastTickSize " + lastTickSize);
			return;
		}
		lastTrade = new JSONObject();
		// Guess last trade side by price difference.
		if (bidPrice != null && askPrice != null) {
			if (Math.abs(bidPrice-lastTickPrice) < Math.abs(askPrice-lastTickPrice))
				lastTrade.put("T", "SELL");
			else
				lastTrade.put("T", "BUY");
		} else
			lastTrade.put("T", "BUY");
		lastTrade.put("p", lastTickPrice);
		lastTrade.put("s", lastTickSize);
		lastTrade.put("v", lastTickVolume);
		lastTrade.put("t", lastTickTime);
		newTicks.set(0, lastTrade);
		newTicksData.set(1, System.currentTimeMillis());
		newTicksData.set(2, delayMs);
		if (broadcastTickLambda != null)
			Redis.exec(broadcastTickLambda);
		cacheLastTrade();
	}

	private void cacheLastTrade() {
		if (tickDataInited == false) return;

		double lastPrice;
		double lastSize;
		if (lastTickPrice == null || lastTickPrice <= 0 )
			lastPrice = 0.0;
		else lastPrice = lastTickPrice;

		if (lastTickSize == null || lastTickSize <= 0)
			lastSize = 0.0;
		else lastSize = lastTickSize;

		cacheTrade = new JSONObject();
		// Guess last trade side by price difference.
		if (bidPrice != null && askPrice != null) {
			if (Math.abs(bidPrice-lastPrice) < Math.abs(askPrice-lastPrice))
				cacheTrade.put("T", "SELL");
			else
				cacheTrade.put("T", "BUY");
		} else
			cacheTrade.put("T", "BUY");

		cacheTrade.put("p", lastPrice);
		cacheTrade.put("s", lastSize);
		cacheTrade.put("v", lastTickVolume);
		cacheTrade.put("t", lastTickTime);
		cacheTicks.set(0, cacheTrade);
		cacheTicksData.set(1, System.currentTimeMillis());
		cacheTicksData.set(2, delayMs);

		if (cacheTickLambda != null)
			Redis.exec(cacheTickLambda);
	}

	@Override
	public void tickString(TickType tickType, String value) {
		if (_debug)
			info(_contract.shownName() + " tickString() tickType " + tickType + " VALUE: " + value);
		switch (tickType) {
			case LAST_TIMESTAMP:
				lastTickTime = Long.parseLong(value) * 1000;
				// lastTickPrice = null; // Could be reused by next tick
				if (delayMs == null || delayMs != 0L)
					delayMs = 0L;
				if (lastTickSize != null)
					recordLastTrade();
				lastTickSize = null; // <- maybe no need
				break;
			// Refer https://interactivebrokers.github.io/tws-api/market_data_type.html
			// If live data is available a request for delayed data would be ignored by TWS
			// DELAYED types
			case DELAYED_LAST_TIMESTAMP:
				lastTickTime = Long.parseLong(value) * 1000;

				long exchangeTimeMs = Long.parseLong(value) * 1000L;
				long currentTimeMs = System.currentTimeMillis();
				long tmpDelayMs = ((currentTimeMs - exchangeTimeMs) / 60000) * 60000;
				if (delayMs == null || tmpDelayMs < delayMs)
					delayMs = tmpDelayMs;
				if (lastTickSize != null)
					recordLastTrade();
				lastTickSize = null; // <- maybe no need
				break;
			default:
				info(_contract.shownName() + " tickString() tickType " + tickType + " VALUE: " + value);
				break;
		}
	}

	@Override
	public void tickSnapshotEnd() {
		// This function suddenly does not work. 20200514
		tickDataInited = true;
		broadcastTop(true);
	}

	private void broadcastTop(boolean verbose) {
		if (broadcastTopLambda != null) {
			Redis.exec(broadcastTopLambda);
			if (verbose)
				log(">>> broadcast top " + publishODBKChannel);
		}
	}

	@Override
	public void marketDataType(int marketDataType) {
		// https://interactivebrokers.github.io/tws-api/market_data_type.html
		// Switch to live (1) frozen (2) delayed (3) or delayed frozen (4)
		if (marketDataType == 1) {
			info(_contract.shownName() + " marketDataType() " + marketDataType);
			delayMs = 0L;
		} else {
			warn(_contract.shownName() + " marketDataType() " + marketDataType);
			if (nowMarketDataType == null || nowMarketDataType != marketDataType) {
				delayMs = null;
			}
		}
		Redis.setex(cacheKey, 86400, String.valueOf(marketDataType));
	}

	public void setMktCacheKey(String key) {
		cacheKey = key;
	}

	@Override
	public void tickReqParams(int tickerId, double minTick, String bboExchange, int snapshotPermissions) {
		info(_contract.shownName() + " tickReqParams() tickerId " + tickerId + " minTick " + minTick + " bboExchange " + bboExchange + " snapshotPermissions " + snapshotPermissions);
	}

	public long getSizeMultiplier() {
		return marketDataSizeMultiplier;
	}

}
