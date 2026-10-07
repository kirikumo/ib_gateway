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

package samples.testbed.contracts;

import com.ib.client.protobuf.ContractProto;

public class ContractSamplesProto {

    public static ContractProto.Contract IBMStockAtSmart() {
        //! [IBM_stock_at_smart]
        ContractProto.Contract.Builder contractProtoBuilder = ContractProto.Contract.newBuilder();
        contractProtoBuilder.setSymbol("IBM");
        contractProtoBuilder.setSecType("STK");
        contractProtoBuilder.setExchange("SMART");
        contractProtoBuilder.setCurrency("USD");
        //! [IBM_stock_at_smart]
        return contractProtoBuilder.build();
    }

    public static ContractProto.Contract MSFTStockAtSmart() {
        //! [MSFT_stock_at_smart]
        ContractProto.Contract.Builder contractProtoBuilder = ContractProto.Contract.newBuilder();
        contractProtoBuilder.setSymbol("MSFT");
        contractProtoBuilder.setSecType("STK");
        contractProtoBuilder.setExchange("SMART");
        contractProtoBuilder.setCurrency("USD");
        //! [MSFT_stock_at_smart]
        return contractProtoBuilder.build();
    }
}