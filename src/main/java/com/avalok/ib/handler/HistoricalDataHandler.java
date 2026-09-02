package com.avalok.ib.handler;

import com.ib.controller.ApiController;
import com.ib.controller.Bar;

import static com.bitex.util.DebugUtil.log;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.GregorianCalendar;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bitex.util.Redis;

public class HistoricalDataHandler implements ApiController.IHistoricalDataHandler {
    protected final JSONArray historyBar = new JSONArray();
    private Long id;

    public HistoricalDataHandler(Long _id) {
        id = _id;
    }

    @Override
    public void historicalData(Bar bar) {
        JSONObject j = new JSONObject();
        j.put("timestamp", barEpochSecond(bar));
        j.put("open", bar.open());
        j.put("high", bar.high());
        j.put("low", bar.low());
        j.put("close", bar.close());
        j.put("volume", bar.volume().longValue());
        historyBar.add(j);
    }

    /** 1045 Bar(String) 會把 time() 設成 Long.MAX_VALUE，改從 timeStr 轉 epoch second。 */
    private static long barEpochSecond(Bar bar) {
        if (bar.time() != Long.MAX_VALUE) return bar.time();
        String timeStr = bar.timeStr();
        if (timeStr == null || timeStr.length() == 0) return bar.time();
        if (timeStr.length() == 8) {
            int year = Integer.parseInt(timeStr.substring(0, 4));
            int month = Integer.parseInt(timeStr.substring(4, 6));
            int day = Integer.parseInt(timeStr.substring(6));
            return new GregorianCalendar(year, month - 1, day).getTimeInMillis() / 1000;
        }
        if (timeStr.contains(" ")) {
            try {
                DateTimeFormatter fmt = timeStr.contains("/")
                        ? DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss VV")
                        : DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss z");
                return ZonedDateTime.parse(timeStr, fmt).toEpochSecond();
            } catch (Exception e) {
                return Long.parseLong(timeStr);
            }
        }
        return Long.parseLong(timeStr);
    }

    @Override
    public void historicalDataEnd() {
//    	Redis.setex(null, 30, historyBar);
        String key = "IBGateway:ReqIdHistoricalData:" + id;
        log("Redis -> " + key);
        Redis.setex(key, 30, historyBar);

    }
}
