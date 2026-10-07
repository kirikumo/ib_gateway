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

package samples.rfq;

import com.ib.client.Decimal;
import com.ib.client.Order;
import com.ib.client.OrderType;

public class RfqOrder extends Order {

   public RfqOrder(int clientId, int id, int size) {

      clientId(clientId);
      orderId(id);
      permId(id);
      totalQuantity(Decimal.get(size));
      orderType(OrderType.QUOTE);

      /*
       * Note: this will be overridden by the backend
       *       because it could not keep such order
       *       (and it does not make too much sense)
       */
      transmit(false);
   }
}

