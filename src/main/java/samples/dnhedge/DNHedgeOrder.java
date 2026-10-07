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

package samples.dnhedge;

import com.ib.client.Decimal;
import com.ib.client.Order;
import com.ib.client.OrderType;
import com.ib.client.Types.Action;
import com.ib.client.Types.VolatilityType;

public class DNHedgeOrder extends Order {
   public DNHedgeOrder(int clientId, int id, int size, String account,
      String settlingFirm, int underConId, String designatedLocation) {
      clientId(clientId);
      orderId(id);
      permId(id);

      account(account);
      clearingIntent("AWAY");
      settlingFirm(settlingFirm);

      orderType(OrderType.VOL);
      action(Action.BUY);
      totalQuantity(Decimal.get(size));

      volatility(0.1);
      volatilityType(VolatilityType.Daily);
      continuousUpdate(1);
      deltaNeutralOrderType(OrderType.LMT);

      deltaNeutralConId(underConId);
      deltaNeutralOpenClose("O");
      deltaNeutralShortSale(true);

      deltaNeutralDesignatedLocation(designatedLocation);
      deltaNeutralShortSaleSlot(deltaNeutralDesignatedLocation().length() == 0 ? 1 : 2);
   }
}
