package com.avalok.ib.handler;

import apidemo.AccountSummaryPanel;
import com.alibaba.fastjson.JSONObject;
import com.ib.controller.AccountSummaryTag;
import com.ib.controller.ApiController.IAccountSummaryHandler;

import java.util.HashMap;
import java.util.Map;

import static com.bitex.util.DebugUtil.*;
import com.bitex.util.Redis;
import static com.ib.controller.AccountSummaryTag.AccountType;

public class AccountSummaryHandler implements IAccountSummaryHandler{
    Map<String, Map<String, String>> m_map = new HashMap<>();

    @Override
    public void accountSummary(String account, AccountSummaryTag tag, String value, String currency) {
        Map<String, String> summary = m_map.get(account);
        if (summary == null) summary = new HashMap<>();

        switch (tag){
            case AccountType:
                summary.put("AccountType", value);
                break;
            case NetLiquidation:
                summary.put("NetLiquidation", value);
                break;
            case TotalCashValue:
                summary.put("TotalCashValue", value);
                break;
            case SettledCash:
                summary.put("SettledCash", value);
                break;
            case AccruedCash:
                summary.put("AccruedCash", value);
                break;
            case BuyingPower:
                summary.put("BuyingPower", value);
                break;
            case EquityWithLoanValue:
                summary.put("EquityWithLoanValue", value);
                break;
            case RegTEquity:
                summary.put("RegTEquity", value);
                break;
            case RegTMargin:
                summary.put("RegTMargin", value);
                break;
            case InitMarginReq:
                summary.put("InitMarginReq", value);
                break;
            case MaintMarginReq:
                summary.put("MaintMarginReq", value);
                break;
            case ExcessLiquidity:
                summary.put("ExcessLiquidity", value);
                break;
            case Cushion:
                summary.put("Cushion", value);
                break;
            case LookAheadInitMarginReq:
                summary.put("LookAheadInitMarginReq", value);
                break;
            case LookAheadMaintMarginReq:
                summary.put("LookAheadMaintMarginReq", value);
                break;
            case LookAheadAvailableFunds:
                summary.put("LookAheadAvailableFunds", value);
                break;
            case LookAheadExcessLiquidity:
                summary.put("LookAheadExcessLiquidity", value);
                break;
            case Leverage:
                summary.put("Leverage", value);
                break;
            case AvailableFunds:
                summary.put("AvailableFunds", value);
                break;
//            case FullAvailableFunds:
//                summary.put("FullAvailableFunds", value);
//                break;
//            case FullExcessLiquidity:
//                summary.put("FullAvailableFunds", value);
//                break;
//            case FullInitMarginReq:
//                summary.put("FullInitMarginReq", value);
//                break;
//            case FullMaintMarginReq:
//                summary.put("FullMaintMarginReq", value);
//                break;
            case GrossPositionValue:
                summary.put("GrossPositionValue", value);
                break;
//            default:
//                log("Not handle tag: " + tag + " | value: " + value);
        }
        m_map.put(account, summary);
//        log("account: "+account+" tag: "+tag + " value: " + value + " currency: "+currency);

    }

    @Override
    public void accountSummaryEnd() {
        JSONObject j = new JSONObject();
        m_map.forEach((account,v)-> {
            String key = "IBGateway:Summary:" + account;
            j.put("data", v);
            j.put("updateTime", System.currentTimeMillis());
            Redis.set(key, j.toJSONString());
            log("Redis -> " + key);
        });


    }
}
