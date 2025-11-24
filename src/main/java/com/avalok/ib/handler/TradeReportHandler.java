package com.avalok.ib.handler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.bitex.util.Redis;
import com.ib.client.CommissionReport;
import com.ib.client.Contract;
import com.ib.client.Execution;
import com.ib.controller.ApiController;
import redis.clients.jedis.Jedis;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static com.bitex.util.DebugUtil.log;

public class TradeReportHandler implements ApiController.ITradeReportHandler {
    Map<String, JSONObject> result = new HashMap<>();

    @Override
    public void tradeReport(String tradeKey, Contract contract, Execution execution) {
        if (!result.containsKey(tradeKey)) {
            result.put(tradeKey, new JSONObject());
        }
        JSONObject j = result.get(tradeKey);
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

        // Redis.exec(new Consumer<Jedis>() {
        //     @Override
        //     public void accept(Jedis t) {
        //         String key = "TradeReport:"+execution.acctNumber();
        //         JSONObject j = new JSONObject();
        //         j.put("orderId", execution.orderId());
        //         j.put("clientId", execution.clientId());
        //         j.put("execId", execution.execId());
        //         j.put("time", execution.time());
        //         j.put("acctNumber", execution.acctNumber());
        //         j.put("exchange", execution.exchange());
        //         j.put("side", execution.side());
        //         j.put("shares", execution.shares().longValue());
        //         j.put("price", execution.price());
        //         j.put("permId", execution.permId());
        //         j.put("liquidation", execution.liquidation());
        //         j.put("cumQty", execution.cumQty().longValue());
        //         j.put("avgPrice", execution.avgPrice());
        //         j.put("orderRef", execution.orderRef());
        //         j.put("evRule", execution.evRule());
        //         j.put("evMultiplier", execution.evMultiplier());
        //         j.put("modelCode", execution.modelCode());
        //         j.put("lastLiquidity", execution.lastLiquidityStr());

        //         t.hset(key, tradeKey, j.toJSONString());
        //     }
        // });
    }

    @Override
    public void tradeReportEnd() {
         Redis.exec(new Consumer<Jedis>() {
             @Override
             public void accept(Jedis t) {
                 result.forEach((tradeKey, v) -> {
                     String acctNumber = v.getString("acctNumber");
                     String key = "TradeReport:"+acctNumber;
                     v.put("tradeKey", tradeKey);
                     t.hset(key, tradeKey, v.toJSONString());
                 });
              }
          });

        log("tradeReportEnd");
    }

    @Override
    public void commissionReport(String tradeKey, CommissionReport commissionReport) {
//        log("tradeKey: " + tradeKey + " commissionReport: " + commissionReport);
        if (!result.containsKey(tradeKey)) {
            result.put(tradeKey, new JSONObject());
        }
        JSONObject j = result.get(tradeKey);
        j.put("commission", commissionReport.commission());
        j.put("execId", commissionReport.execId());
        j.put("currency", commissionReport.currency());
        j.put("realizedPNL", commissionReport.realizedPNL());
        j.put("yield", commissionReport.yield());
        j.put("yieldRedemptionDate", commissionReport.yieldRedemptionDate());
    }
}
