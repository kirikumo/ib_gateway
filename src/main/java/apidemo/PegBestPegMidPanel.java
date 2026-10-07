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

import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import javax.swing.JCheckBox;
import javax.swing.JDialog;

import com.ib.client.Order;
import com.ib.client.OrderCondition;

import apidemo.util.UpperField;

public class PegBestPegMidPanel extends OnOKPanel {
    private final JDialog m_parentDlg;
    private final Order m_order;
    final UpperField m_minTradeQty = new UpperField();
    final UpperField m_minCompeteSize = new UpperField();
    final UpperField m_competeAgainstBestOffset = new UpperField();
    private final JCheckBox m_competeAgainstBestOffsetUpToMid = new JCheckBox("Compete Against Best Offset Up To Mid");
    final UpperField m_midOffsetAtWhole = new UpperField();
    final UpperField m_midOffsetAtHalf = new UpperField();

    public PegBestPegMidPanel(JDialog parentDlg, Order order) {
        m_parentDlg = parentDlg;
        m_order = order;
        m_minTradeQty.setText(m_order.minTradeQty());
        m_minCompeteSize.setText(m_order.minCompeteSize());
        m_competeAgainstBestOffset.setText(m_order.competeAgainstBestOffset());
        m_competeAgainstBestOffsetUpToMid.setSelected(m_order.competeAgainstBestOffset() == Order.COMPETE_AGAINST_BEST_OFFSET_UP_TO_MID);
        m_midOffsetAtWhole.setText(m_order.midOffsetAtWhole());
        m_midOffsetAtHalf.setText(m_order.midOffsetAtHalf());

        add("Min Trade Qty", m_minTradeQty);
        add("Min Compete Size", m_minCompeteSize);
        add("Compete Against Best Offset", m_competeAgainstBestOffset);
        add(m_competeAgainstBestOffsetUpToMid);
        add("Mid Offset At Whole", m_midOffsetAtWhole);
        add("Mid Offset At Half", m_midOffsetAtHalf);

        // add listeners
        m_competeAgainstBestOffsetUpToMid.addItemListener(new ItemListener() {
            @Override public void itemStateChanged(ItemEvent e) {
                m_competeAgainstBestOffset.setEnabled(e.getStateChange() != ItemEvent.SELECTED);
            }
        });
    }

    public OrderCondition onOK() {
        m_order.minTradeQty(m_minTradeQty.getInt());
        m_order.minCompeteSize(m_minCompeteSize.getInt());
        m_order.competeAgainstBestOffset(m_competeAgainstBestOffsetUpToMid.isSelected() ? Order.COMPETE_AGAINST_BEST_OFFSET_UP_TO_MID : m_competeAgainstBestOffset.getDouble());
        m_order.minCompeteSize(m_minCompeteSize.getInt());
        m_order.midOffsetAtWhole(m_midOffsetAtWhole.getDouble());
        m_order.midOffsetAtHalf(m_midOffsetAtHalf.getDouble());

        return null;
    }
}