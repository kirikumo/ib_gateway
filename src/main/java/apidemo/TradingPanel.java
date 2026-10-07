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

import java.awt.Dimension;

import javax.swing.BoxLayout;

import apidemo.util.NewTabbedPanel.NewTabPanel;


public class TradingPanel extends NewTabPanel {
	private final OrdersPanel m_ordersPanel = new OrdersPanel();
    private final CompletedOrdersPanel m_completedOrdersPanel = new CompletedOrdersPanel();
	private final TradesPanel m_tradesPanel = new TradesPanel();
	
	TradingPanel() {
		m_ordersPanel.setPreferredSize( new Dimension( 1, 10000));
        m_completedOrdersPanel.setPreferredSize( new Dimension( 1, 10000));
		m_tradesPanel.setPreferredSize( new Dimension( 1, 10000));
		
		setLayout( new BoxLayout( this, BoxLayout.Y_AXIS));
		add( m_ordersPanel);
        add( m_completedOrdersPanel);
		add( m_tradesPanel);
	}

	/** Called when the tab is first visited. */
	@Override public void activated() {
		m_ordersPanel.activated();
        m_completedOrdersPanel.activated();
		m_tradesPanel.activated();
	}

	/** Called when the tab is closed by clicking the X. */
	@Override public void closed() {
	}
}
