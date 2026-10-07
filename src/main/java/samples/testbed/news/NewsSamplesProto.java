/*
 * Java TWS API Samples
 *
 * Copyright (C) 2013-2026  Interactive Brokers LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package samples.testbed.news;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import com.ib.client.protobuf.HistoricalNewsRequestProto;

public class NewsSamplesProto {

    public static HistoricalNewsRequestProto.HistoricalNewsRequest HistoricalNewsRequestWithEndTime(int reqId) {
        //! [historical_news_request_with_end_time]
        HistoricalNewsRequestProto.HistoricalNewsRequest.Builder historicalNewsRequestBuilder = HistoricalNewsRequestProto.HistoricalNewsRequest.newBuilder();
        historicalNewsRequestBuilder.setReqId(reqId);
        historicalNewsRequestBuilder.setConId(8314);
        historicalNewsRequestBuilder.setProviderCodes("BRFUPDN+BRFG");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.0");
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -10);
        historicalNewsRequestBuilder.setEndDateTime(df.format(cal.getTime()));
        historicalNewsRequestBuilder.setTotalResults(10);
        return historicalNewsRequestBuilder.build();
        //! [historical_news_request_with_end_time]
    }

    public static HistoricalNewsRequestProto.HistoricalNewsRequest HistoricalNewsRequestWithStartTime(int reqId) {
        //! [historical_news_request_with_start_time]
        HistoricalNewsRequestProto.HistoricalNewsRequest.Builder historicalNewsRequestBuilder = HistoricalNewsRequestProto.HistoricalNewsRequest.newBuilder();
        historicalNewsRequestBuilder.setReqId(reqId);
        historicalNewsRequestBuilder.setConId(8314);
        historicalNewsRequestBuilder.setProviderCodes("BRFUPDN+BRFG");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.0");
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -10);
        historicalNewsRequestBuilder.setStartDateTime(df.format(cal.getTime()));
        historicalNewsRequestBuilder.setTotalResults(10);
        return historicalNewsRequestBuilder.build();
        //! [historical_news_request_with_start_time]
    }
}