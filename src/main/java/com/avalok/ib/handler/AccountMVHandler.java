package com.avalok.ib.handler;

import static com.bitex.util.DebugUtil.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.alibaba.fastjson.JSONObject;
import com.avalok.ib.IBContract;
import com.avalok.ib.rm.RmAccountFeed;
import com.bitex.util.Redis;
import com.ib.controller.Position;
import com.ib.controller.ApiController.IAccountHandler;


public class AccountMVHandler implements IAccountHandler {

	/** Test only. Stands in for the old balance Redis write. Production leaves this unset. */
	@FunctionalInterface
	public interface BalanceWriter {
		void write(String key, int ttlSeconds, Object value);
	}

	private BalanceWriter balanceWriter;
	private RmAccountFeed rmFeed;

	public void setBalanceWriter(BalanceWriter writer) { balanceWriter = writer; }
	public void setRmFeed(RmAccountFeed feed) { rmFeed = feed; }

	// Modify _tmpData before _dataInit
	// Modify _data directly after _dataInit
	private boolean _dataInit = false;
	private Map<String, Map<String, JSONObject>> _data = new ConcurrentHashMap<>();
	private Map<String, Map<String, JSONObject>> _tmpData = new ConcurrentHashMap<>();
//	public List<IBContract> ibc_cache = new ArrayList<>();
	public Map<String, IBContract> ibc_cache = new ConcurrentHashMap<>();

	@Override
	public synchronized void accountValue(String account, String key, String desc1, String desc2) {
		try {
		accountValueBody(account, key, desc1, desc2);
		} finally {
			if (rmFeed != null) rmFeed.onValue(account, key, desc1, desc2);
		}
	}

	private void accountValueBody(String account, String key, String desc1, String desc2) {
		// Example:
		// AccountCode null U8620103
		// AccountOrGroup BASE zwyang
		// AccountOrGroup HKD zwyang
		// AccountReady null true
		// AccountType null INDIVIDUAL
		// CashBalance HKD 20000.00
		// CashBalance USD 484333.21
		// FullInitMarginReq USD 15663.90
		// What we have interest is how much balance remained in currency.
		JSONObject j = new JSONObject();
		if (desc2 != null && desc2.equals("BASE")) return; // Skip all info in BASE currency. 
		String asset = null;
		if (key.equals("CashBalance")) {
			j.put("type", "cash");
			j.put("currency", desc2);
			j.put("balance", desc1);
			asset = desc2;
		} else return;
		if (_dataInit) {
			_data.putIfAbsent(account, new ConcurrentHashMap<String, JSONObject>());
			_data.get(account).put(asset, j);
		} else {
			_tmpData.putIfAbsent(account, new ConcurrentHashMap<String, JSONObject>());
			_tmpData.get(account).put(asset, j);
		}
	}

	@Override
	public void accountTime(String timeStamp) {
		// info("<-- AccountMV accountTime " + timeStamp);
	}

	@Override
	public synchronized void updatePortfolio(Position position) {
		try {
		updatePortfolioBody(position);
		} finally {
			if (rmFeed != null) rmFeed.onPortfolio(position);
		}
	}

	private void updatePortfolioBody(Position position) {
		IBContract ibc = new IBContract(position.contract());
		if (!ibc_cache.containsKey(ibc.pair())) {
			ibc_cache.put(ibc.pair(), ibc);
		}

		ContractDetailsHandler.findDetails(ibc); // Auto query details for instruments in portfolio
		String account = position.account();
		JSONObject j = new JSONObject();
		j.put("type", "position");
		j.put("contract", ibc.toJSON());
		j.put("pos", position.position().longValue());
		j.put("avgCost", position.averageCost());

		j.put("realPnl", position.realPnl());
		j.put("unrealPnl", position.unrealPnl());
		j.put("marketPrice", position.marketPrice());
		j.put("marketValue", position.marketValue());
		j.put("shortName", ibc.shownName());

		if (_dataInit) {
			_data.putIfAbsent(account, new ConcurrentHashMap<String, JSONObject>());
			_data.get(account).put(ibc.shownName(), j);
		} else {
			_tmpData.putIfAbsent(account, new ConcurrentHashMap<String, JSONObject>());
			_tmpData.get(account).put(ibc.shownName(), j);
		}
	}

	@Override
	public synchronized void accountDownloadEnd(String account) {
		info("<-- AccountMV Download End " + account);
		if (_dataInit == false) {
			_dataInit = true;
			_data = _tmpData;
			_tmpData = new ConcurrentHashMap<>();
		}
		writePosition(account);
		if (rmFeed != null) rmFeed.onDownloadEnd(account);
	}

	protected void writePosition(String account) {
		if (account == null) return;
		Map<String, JSONObject> pos = _data.get(account);
		if (pos == null) return;
		final String key = "IBGateway:" + account + ":balance";
		if (balanceWriter != null) balanceWriter.write(key, 2764800, pos);
		else Redis.setex(key, 2764800, pos);
	}

	public Map<String, Map<String, JSONObject>> position() {
		if (_dataInit)
			return _data;
		else
			return null;
	}
}
