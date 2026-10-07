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

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;

import com.ib.controller.ApiController.IMarketValueSummaryHandler;
import com.ib.controller.MarketValueTag;

import apidemo.AccountInfoPanel.MktValModel;
import apidemo.AccountInfoPanel.Table;
import apidemo.util.HtmlButton;
import apidemo.util.NewTabbedPanel.NewTabPanel;
import apidemo.util.VerticalPanel;

public class MarketValueSummaryPanel extends NewTabPanel implements IMarketValueSummaryHandler {
	private MktValModel m_model = new MktValModel();

	MarketValueSummaryPanel() {
		HtmlButton sub = new HtmlButton( "Subscribe") {
			protected void actionPerformed() {
				subscribe();
			}
		};
		
		HtmlButton desub = new HtmlButton( "Desubscribe") {
			protected void actionPerformed() {
				desubscribe();
			}
		};
		
		JPanel buts = new VerticalPanel();
		buts.add( sub);
		buts.add( desub);

		JTable table = new Table( m_model, 2);
		JScrollPane scroll = new JScrollPane( table);
		
		setLayout( new BorderLayout() );
		add( scroll);
		add( buts, BorderLayout.EAST);
	}

	/** Called when the tab is first visited. */
	@Override public void activated() {
		subscribe();
	}

	/** Called when the tab is closed by clicking the X. */
	@Override public void closed() {
		desubscribe();
	}

	private void subscribe() {
		ApiDemo.INSTANCE.controller().reqMarketValueSummary( "All", this);
	}

	private void desubscribe() {
		ApiDemo.INSTANCE.controller().cancelMarketValueSummary( this);
		m_model.clear();
	}

	@Override public void marketValueSummary(String account, MarketValueTag tag, String value, String currency) {
		m_model.handle( account, currency, tag, value);
	}

	@Override public void marketValueSummaryEnd() {
	}
}
