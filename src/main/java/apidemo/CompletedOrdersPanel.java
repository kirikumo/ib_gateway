/*
 * Java ApiDemo
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

package apidemo;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;

import com.ib.client.Contract;
import com.ib.client.Order;
import com.ib.client.OrderState;
import com.ib.client.Util;
import com.ib.controller.ApiController.ICompletedOrdersHandler;

import apidemo.util.HtmlButton;

public class CompletedOrdersPanel extends JPanel implements ICompletedOrdersHandler {
    private List<CompletedOrder> m_completedOrders = new ArrayList<>();
    private Model m_model = new Model();

    CompletedOrdersPanel() {
        JTable table = new JTable(m_model);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(new TitledBorder("Completed Orders"));

        HtmlButton but = new HtmlButton("Refresh") {
            @Override public void actionPerformed() {
                onRefresh();
            }
        };

        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        p.add(but);

        setLayout(new BorderLayout());
        add(scroll);
        add(p, BorderLayout.SOUTH);
    }

    public void activated() {
        onRefresh();
    }

    private void onRefresh() {
        m_completedOrders.clear();
        ApiDemo.INSTANCE.controller().reqCompletedOrders(this);
    }

    private class Model extends AbstractTableModel {
        @Override public int getRowCount() {
            return m_completedOrders.size();
        }

        @Override public int getColumnCount() {
            return 17;
        }

        @Override public String getColumnName(int col) {
            switch( col) {
                case 0: return "Perm ID";
                case 1: return "Parent Perm ID";
                case 2: return "Account";
                case 3: return "Action";
                case 4: return "Quantity";
                case 5: return "Cash Qty";
                case 6: return "Filled Qty";
                case 7: return "Lmt Price";
                case 8: return "Aux Price";
                case 9: return "Contract";
                case 10: return "Cust Acct";
                case 11: return "Prof Cust";
                case 12: return "Submitter";
                case 13: return "Imbalance Only";
                case 14: return "Status";
                case 15: return "Completed Time";
                case 16: return "Completed Status";

                default: return null;
            }
        }

        @Override public Object getValueAt(int row, int col) {
            CompletedOrder completedOrder = m_completedOrders.get( row);

            switch( col) {
                case 0: return Util.LongMaxString(completedOrder.m_order.permId());
                case 1: return Util.LongMaxString(completedOrder.m_order.parentPermId());
                case 2: return completedOrder.m_order.account();
                case 3: return completedOrder.m_order.action();
                case 4: return completedOrder.m_order.totalQuantity();
                case 5: return Util.DoubleMaxString(completedOrder.m_order.cashQty());
                case 6: return completedOrder.m_order.filledQuantity();
                case 7: return Util.DoubleMaxString(completedOrder.m_order.lmtPrice());
                case 8: return Util.DoubleMaxString(completedOrder.m_order.auxPrice());
                case 9: return completedOrder.m_contract.textDescription();
                case 10: return completedOrder.m_order.customerAccount();
                case 11: return completedOrder.m_order.professionalCustomer();
                case 12: return completedOrder.m_order.submitter();
                case 13: return completedOrder.m_order.imbalanceOnly();
                case 14: return completedOrder.m_orderState.status();
                case 15: return completedOrder.m_orderState.completedTime();
                case 16: return completedOrder.m_orderState.completedStatus();
                default: return null;
            }
        }
    }

    static class CompletedOrder {
        Contract m_contract;
        Order m_order;
        OrderState m_orderState;

        CompletedOrder(Contract contract, Order order, OrderState orderState) {
            m_contract = contract;
            m_order = order;
            m_orderState = orderState;
        }
    }

    @Override
    public void completedOrder(Contract contract, Order order, OrderState orderState) {
        CompletedOrder completedOrder = new CompletedOrder(contract, order, orderState);
        m_completedOrders.add(completedOrder);
        m_model.fireTableDataChanged();
    }

    @Override
    public void completedOrdersEnd() {
    }
}
