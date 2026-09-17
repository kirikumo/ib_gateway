package com.avalok.ib.handler;

import com.alibaba.fastjson.JSONObject;
import com.ib.controller.AccountSummaryTag;
import com.ib.controller.ApiController.IAccountSummaryHandler;

import java.util.HashMap;
import java.util.Map;

import static com.bitex.util.DebugUtil.*;
import com.bitex.util.Redis;

public class AccountSummaryHandler implements IAccountSummaryHandler{
    Map<String, Map<String, String>> m_map = new HashMap<>();
    private boolean snapshotComplete = false;
    private final SummaryWriter writer;

    interface SummaryWriter {
        void write(String key, String json);
    }

    public AccountSummaryHandler() {
        this((key, json) -> {
            Redis.setex(key, 2764800, json);
            log("Redis -> " + key);
        });
    }

    AccountSummaryHandler(SummaryWriter writer) {
        this.writer = writer;
    }

    public void beginSnapshot() {
        snapshotComplete = false;
    }

//    https://ibkrcampus.com/ibkr-api-page/trader-workstation-api/#account-value-keys
    @Override
    public void accountSummary(String account, AccountSummaryTag tag, String value, String currency) {
        Map<String, String> summary = m_map.get(account);
        if (summary == null) summary = new HashMap<>();
        // protobuf omits currency on non-price tags (e.g. AccountType); do not lock in ""
        if (currency != null && !currency.isEmpty()) {
            String existing = summary.get("BaseCurrency");
            if (existing == null || existing.isEmpty()) {
                summary.put("BaseCurrency", currency);
            }
        }

        switch (tag){
            case AccountType:
                summary.put("AccountType", value);
//                m_map.get(account).put("AccountType", value);
                break;
            case NetLiquidation:
                summary.put("NetLiquidation", value);
//                m_map.get(account).put("NetLiquidation", value);
                break;
            case TotalCashValue:
                summary.put("TotalCashValue", value);
//                m_map.get(account).put("TotalCashValue", value);
                break;
            case SettledCash:
                summary.put("SettledCash", value);
//                m_map.get(account).put("SettledCash", value);
                break;
            case AccruedCash:
                summary.put("AccruedCash", value);
//                m_map.get(account).put("AccruedCash", value);
                break;
            case BuyingPower:
                summary.put("BuyingPower", value);
//                m_map.get(account).put("BuyingPower", value);
                break;
            case EquityWithLoanValue:
                summary.put("EquityWithLoanValue", value);
//                m_map.get(account).put("EquityWithLoanValue", value);
                break;
            case RegTEquity:
                summary.put("RegTEquity", value);
//                m_map.get(account).put("RegTEquity", value);
                break;
            case RegTMargin:
                summary.put("RegTMargin", value);
//                m_map.get(account).put("RegTMargin", value);
                break;
            case InitMarginReq:
                summary.put("InitMarginReq", value);
//                m_map.get(account).put("InitMarginReq", value);
                break;
            case MaintMarginReq:
                summary.put("MaintMarginReq", value);
//                m_map.get(account).put("MaintMarginReq", value);
                break;
            case ExcessLiquidity:
                summary.put("ExcessLiquidity", value);
//                m_map.get(account).put("ExcessLiquidity", value);
                break;
            case Cushion:
                summary.put("Cushion", value);
//                m_map.get(account).put("Cushion", value);
                break;
            case LookAheadInitMarginReq:
                summary.put("LookAheadInitMarginReq", value);
//                m_map.get(account).put("LookAheadInitMarginReq", value);
                break;
            case LookAheadMaintMarginReq:
                summary.put("LookAheadMaintMarginReq", value);
//                m_map.get(account).put("LookAheadMaintMarginReq", value);
                break;
            case LookAheadAvailableFunds:
                summary.put("LookAheadAvailableFunds", value);
//                m_map.get(account).put("LookAheadAvailableFunds", value);
                break;
            case LookAheadExcessLiquidity:
                summary.put("LookAheadExcessLiquidity", value);
//                m_map.get(account).put("LookAheadExcessLiquidity", value);
                break;
            case Leverage:
                summary.put("Leverage", value);
//                m_map.get(account).put("Leverage", value);
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
            case PreviousDayEquityWithLoanValue:
                summary.put("PreviousDayEquityWithLoanValue", value);
                break;
            case HighestSeverity:
                summary.put("HighestSeverity", value);
                break;
            // default:
            //     summary.put(tag.toString(), value);

        }
        m_map.put(account, summary);
        if (snapshotComplete) {
            writeAccount(account);
        }
    }

    @Override
    public void accountSummaryEnd() {
        snapshotComplete = true;
        for (String account : m_map.keySet()) {
            writeAccount(account);
        }
    }

    private void writeAccount(String account) {
        Map<String, String> v = m_map.get(account);
        if (v == null) return;
        JSONObject j = new JSONObject();
        j.put("data", v);
        j.put("updateTime", System.currentTimeMillis());
        writer.write("IBGateway:Summary:" + account, j.toJSONString());
    }
}
