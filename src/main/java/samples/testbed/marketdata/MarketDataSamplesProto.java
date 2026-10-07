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

package samples.testbed.marketdata;

import com.ib.client.protobuf.CancelMarketDataProto;
import com.ib.client.protobuf.ContractProto;
import com.ib.client.protobuf.MarketDataRequestProto;

public class MarketDataSamplesProto {

    public static MarketDataRequestProto.MarketDataRequest OddLotBidAskQuotesRequest(int reqId, ContractProto.Contract contract) {
        //! [odd_lot_bid_ask_quotes_request]
        MarketDataRequestProto.MarketDataRequest.Builder marketDataRequestBuilder = MarketDataRequestProto.MarketDataRequest.newBuilder();
        marketDataRequestBuilder.setReqId(reqId);
        marketDataRequestBuilder.setContract(contract);
        marketDataRequestBuilder.setGenericTickList("787");
        //! [odd_lot_bid_ask_quotes_request]
        return marketDataRequestBuilder.build();
    }

    public static MarketDataRequestProto.MarketDataRequest RegulatorySnapshotRequest(int reqId, ContractProto.Contract contract) {
        //! [regulatory_snapshot_request]
        MarketDataRequestProto.MarketDataRequest.Builder marketDataRequestBuilder = MarketDataRequestProto.MarketDataRequest.newBuilder();
        marketDataRequestBuilder.setReqId(reqId);
        marketDataRequestBuilder.setContract(contract);
        marketDataRequestBuilder.setRegulatorySnapshot(true);
        //! [regulatory_snapshot_request]
        return marketDataRequestBuilder.build();
    }

    public static CancelMarketDataProto.CancelMarketData CancelMarketDataRequest(int reqId) {
        //! [cancel_market_data_request]
        CancelMarketDataProto.CancelMarketData.Builder cancelMarketDataBuilder = CancelMarketDataProto.CancelMarketData.newBuilder();
        cancelMarketDataBuilder.setReqId(reqId);
        //! [cancel_market_data_request]
        return cancelMarketDataBuilder.build();
    }
}
