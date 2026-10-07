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
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

import com.ib.client.DepthMktDataDescription;
import com.ib.controller.ApiController.IMktDepthExchangesHandler;

import apidemo.AccountInfoPanel.Table;
import apidemo.util.HtmlButton;
import apidemo.util.NewTabbedPanel.NewTabPanel;
import apidemo.util.VerticalPanel;


public class MktDepthExchangesPanel extends NewTabPanel {
	private MktDepthExchangesModel m_model = new MktDepthExchangesModel();

	MktDepthExchangesPanel() {
		HtmlButton reqMktDepthExchangesButton = new HtmlButton( "Request Market Depth Exchanges") {
			protected void actionPerformed() {
				reqMktDepthExchanges();
			}
		};

		HtmlButton clearMktDepthExchangesButton = new HtmlButton( "Clear MarketDepth Exchanges") {
			protected void actionPerformed() {
				clearMktDepthExchanges();
			}
		};

		JPanel buts = new VerticalPanel();
		buts.add( reqMktDepthExchangesButton);
		buts.add( clearMktDepthExchangesButton);

		JTable table = new Table( m_model, 2);
		JScrollPane scroll = new JScrollPane( table);
		scroll.setPreferredSize(new Dimension(100, 100));
		setLayout( new BorderLayout() );
		add( scroll);
		add( buts, BorderLayout.EAST);
	}

	/** Called when the tab is first visited. */
	@Override public void activated() { /* noop */ }

	/** Called when the tab is closed by clicking the X. */
	@Override public void closed() {
		clearMktDepthExchanges();
	}

	private void reqMktDepthExchanges() {
		ApiDemo.INSTANCE.controller().reqMktDepthExchanges( m_model);
	}

	private void clearMktDepthExchanges() {
		m_model.clear();
	}

	private class MktDepthExchangesModel extends AbstractTableModel implements IMktDepthExchangesHandler {
		List<DepthMktDataDescriptionRow> m_list = new ArrayList<>();

		@Override public void mktDepthExchanges(DepthMktDataDescription[] depthMktDataDescriptions) {
			for (DepthMktDataDescription depthMktDataDescription : depthMktDataDescriptions){
				DepthMktDataDescriptionRow row = new DepthMktDataDescriptionRow();
				m_list.add( row);
				row.update(depthMktDataDescription.exchange(), depthMktDataDescription.secType(), 
						depthMktDataDescription.listingExch(), depthMktDataDescription.serviceDataType(),
						depthMktDataDescription.aggGroup());
			}
			m_model.fireTableDataChanged();
		}

		public void clear() {
			m_list.clear();
			fireTableDataChanged();
		}

		@Override public int getRowCount() {
			return m_list.size();
		}

		@Override public int getColumnCount() {
			return 5;
		}

		@Override public String getColumnName(int col) {
			switch( col) {
				case 0: return "Exchange";
				case 1: return "Security Type";
				case 2: return "Listing Exch";
				case 3: return "Service Data Type";
				case 4: return "Agg Group";
				default: return null;
			}
		}

		@Override public Object getValueAt(int rowIn, int col) {
			DepthMktDataDescriptionRow row = m_list.get( rowIn);
			
			switch( col) {
				case 0: return row.m_exchange;
				case 1: return row.m_secType;
				case 2: return row.m_listingExch;
				case 3: return row.m_serviceDataType;
				case 4: return row.m_aggGroup;
				default: return null;
			}
		}
	}

	private static class DepthMktDataDescriptionRow {
		String m_exchange;
		String m_secType;
		String m_listingExch;
		String m_serviceDataType;
		String m_aggGroup;

		void update(String exchange, String secType, String listingExch, String serviceDataType, int aggGroup) {
			m_exchange = exchange;
			m_secType = secType;
			m_listingExch = listingExch;
			m_serviceDataType = serviceDataType;
			m_aggGroup = (aggGroup != Integer.MAX_VALUE ? String.valueOf(aggGroup) : "");
		}
	}
}
