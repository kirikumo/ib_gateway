package com.avalok.ib.handler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.bitex.util.Redis;
import com.ib.client.CommissionReport;
import com.ib.client.Contract;
import com.ib.client.Execution;
import com.ib.controller.ApiController;
import redis.clients.jedis.Jedis;

import java.util.function.Consumer;

import static com.bitex.util.DebugUtil.log;

public class TradeReportHandler implements ApiController.ITradeReportHandler {
    @Override
    public void tradeReport(String tradeKey, Contract contract, Execution execution) {
        Redis.exec(new Consumer<Jedis>() {
            @Override
            public void accept(Jedis t) {
                String key = "TradeReport:"+execution.acctNumber();
                JSONObject j = new JSONObject();
                j.put("orderId", execution.orderId());
                j.put("clientId", execution.clientId());
                j.put("execId", execution.execId());
                j.put("time", execution.time());
                j.put("acctNumber", execution.acctNumber());
                j.put("exchange", execution.exchange());
                j.put("side", execution.side());
                j.put("shares", execution.shares().longValue());
                j.put("price", execution.price());
                j.put("permId", execution.permId());
                j.put("liquidation", execution.liquidation());
                j.put("cumQty", execution.cumQty().longValue());
                j.put("avgPrice", execution.avgPrice());
                j.put("orderRef", execution.orderRef());
                j.put("evRule", execution.evRule());
                j.put("evMultiplier", execution.evMultiplier());
                j.put("modelCode", execution.modelCode());
                j.put("lastLiquidity", execution.lastLiquidityStr());

                t.hset(key, tradeKey, j.toJSONString());
            }
        });
    }

    @Override
    public void tradeReportEnd() {
        log("tradeReportEnd");
    }

    @Override
    public void commissionReport(String tradeKey, CommissionReport commissionReport) {
        log("tradeKey: " + tradeKey + " commissionReport: " + commissionReport);
    }
}
