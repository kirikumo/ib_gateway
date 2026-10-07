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

package samples.testbed.orders;

import com.ib.client.protobuf.ContractProto;
import com.ib.client.protobuf.OrderProto;
import com.ib.client.protobuf.PlaceOrderRequestProto;
import com.ib.client.Decimal;

public class OrderSamplesProto {

    public static PlaceOrderRequestProto.PlaceOrderRequest createPlaceOrderRequest(int orderId, ContractProto.Contract contractProto, OrderProto.Order orderProto) {
        // ! [place_order_request]
        PlaceOrderRequestProto.PlaceOrderRequest.Builder placeOrderRequestBuilder = PlaceOrderRequestProto.PlaceOrderRequest.newBuilder();
        placeOrderRequestBuilder.setOrderId(orderId);
        placeOrderRequestBuilder.setContract(contractProto);
        placeOrderRequestBuilder.setOrder(orderProto);
        // ! [place_order_request]
        return placeOrderRequestBuilder.build();
    }

    public static OrderProto.Order LimitOrder(String action, Decimal quantity, double limitPrice, boolean transmit) {
        // ! [limit_order]
        OrderProto.Order.Builder orderProtoBuilder = OrderProto.Order.newBuilder();
        orderProtoBuilder.setAction(action);
        orderProtoBuilder.setOrderType("LMT");
        orderProtoBuilder.setTotalQuantity(quantity.toString());
        orderProtoBuilder.setLmtPrice(limitPrice);
        orderProtoBuilder.setTif("DAY");
        orderProtoBuilder.setTransmit(transmit);
        // ! [limit_order]
        return orderProtoBuilder.build();
    }

    public static OrderProto.Order BetaHedgeOrder(int parentId, String action, String hedgeParam, int hedgeMaxSize, boolean transmit) {
        // ! [beta_hedge_order]
        OrderProto.Order.Builder orderProtoBuilder = OrderProto.Order.newBuilder();
        orderProtoBuilder.setParentId(parentId);
        orderProtoBuilder.setAction(action);
        orderProtoBuilder.setOrderType("MKT");
        orderProtoBuilder.setTif("DAY");
        orderProtoBuilder.setHedgeType("B");
        orderProtoBuilder.setHedgeParam(hedgeParam);
        orderProtoBuilder.setHedgeMaxSize(hedgeMaxSize);
        orderProtoBuilder.setTransmit(transmit);
        // ! [beta_hedge_order]
        return orderProtoBuilder.build();
    }
}
