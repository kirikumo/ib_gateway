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
import java.text.DecimalFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import com.ib.client.Contract;
import com.ib.client.ContractDetails;
import com.ib.client.PriceIncrement;
import com.ib.controller.ApiController.IContractDetailsHandler;
import com.ib.controller.ApiController.IMarketRuleHandler;

import apidemo.util.HtmlButton;
import apidemo.util.NewTabbedPanel;
import apidemo.util.NewTabbedPanel.INewTab;
import apidemo.util.VerticalPanel;

class ContractInfoPanel extends JPanel {
	private final Contract m_contract = new Contract();
	private final NewTabbedPanel m_resultsPanels = new NewTabbedPanel();
	private static Set<Integer> m_marketRuleIds = new HashSet<>();
    private final MarketRuleRequestPanel m_marketRuleRequestPanel = new MarketRuleRequestPanel();

	ContractInfoPanel() {
	    NewTabbedPanel m_requestPanels = new NewTabbedPanel();
		m_requestPanels.addTab( "Contract details", new DetailsRequestPanel() );
		m_requestPanels.addTab( "Market Rules", m_marketRuleRequestPanel );

		setLayout( new BorderLayout() );
		add( m_requestPanels, BorderLayout.NORTH);
		add( m_resultsPanels);
	}

	class DetailsRequestPanel extends JPanel {
		ContractPanel m_contractPanel = new ContractPanel( m_contract);

		DetailsRequestPanel() {
			HtmlButton but = new HtmlButton( "Query") {
				@Override protected void actionPerformed() {
					onQuery();
				}
			};

			setLayout( new BoxLayout( this, BoxLayout.X_AXIS) );
			add( m_contractPanel);
			add( Box.createHorizontalStrut(20));
			add( but);
		}

		void onQuery() {
			m_contractPanel.onOK();

			DetailsResultsPanel panel = new DetailsResultsPanel();
			m_resultsPanels.addTab( m_contract.symbol() + " " + "Description", panel, true, true);
			ApiDemo.INSTANCE.controller().reqContractDetails(m_contract, panel);
		}
	}

	class DetailsResultsPanel extends JPanel implements IContractDetailsHandler, INewTab {
		JLabel m_label = new JLabel();
		JTextArea m_text = new JTextArea();

		DetailsResultsPanel() {
			JScrollPane scroll = new JScrollPane( m_text);

			setLayout( new BorderLayout() );
			add( m_label, BorderLayout.NORTH);
			add( scroll);
		}

		@Override public void contractDetails(List<ContractDetails> list) {
 			// set label
			if (list.size() == 0) {
				m_label.setText( "No matching contracts were found");
			}
			else if (list.size() > 1) {
				m_label.setText( list.size() + " contracts returned; showing first contract only");
			}
			else {
				m_label.setText( null);
			}

			// set text
			if (list.size() == 0) {
				m_text.setText( null);
			}
			else {
				m_text.setText( list.get( 0).toString() );
			}
			if (list.size() > 0 && list.get( 0).marketRuleIds() != null) {
				for (String s : list.get( 0).marketRuleIds().split(",")){
					m_marketRuleIds.add(Integer.parseInt(s));
				}
				m_marketRuleRequestPanel.m_marketRuleIdCombo.setModel(new DefaultComboBoxModel<>(m_marketRuleIds.toArray(new Integer[m_marketRuleIds.size()])));
			}
		}

        @Override public void closed() { 
            ApiDemo.INSTANCE.controller().cancelContractData(this);
        }

        @Override public void activated() { }
	}

	class MarketRuleRequestPanel extends JPanel {
		JComboBox<Integer> m_marketRuleIdCombo = new JComboBox<>();

		MarketRuleRequestPanel() {
			m_marketRuleIdCombo.setPreferredSize(new Dimension(130, 20));
			m_marketRuleIdCombo.setEditable(true);

			HtmlButton but = new HtmlButton( "Request Market Rule") {
				@Override protected void actionPerformed() {
					onRequestMarketRule();
				}
			};

			VerticalPanel paramsPanel = new VerticalPanel();
			paramsPanel.add( "Market Rule Id", m_marketRuleIdCombo, Box.createHorizontalStrut(100), but);
			setLayout( new BorderLayout() );
			add( paramsPanel, BorderLayout.NORTH);
		}

		void onRequestMarketRule() {
			MarketRuleResultsPanel panel = new MarketRuleResultsPanel();
			final Object item = m_marketRuleIdCombo.getEditor().getItem();
			if (item != null) {
                final String itemString = item.toString();
                if (!itemString.isEmpty()) {
                    try {
                        int marketRuleId = Integer.parseInt(itemString);
                        m_resultsPanels.addTab("Market Rule Id: " + itemString, panel, true, true);
                        ApiDemo.INSTANCE.controller().reqMarketRule(marketRuleId, panel);
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }
                }
            }
		}
	}

	static class MarketRuleResultsPanel extends JPanel implements IMarketRuleHandler {
		JLabel m_label = new JLabel();
		JTextArea m_text = new JTextArea();

		MarketRuleResultsPanel() {
			JScrollPane scroll = new JScrollPane( m_text);

			setLayout( new BorderLayout() );
			add( m_label, BorderLayout.NORTH);
			add( scroll);
		}

		@Override
		public void marketRule(int marketRuleId, PriceIncrement[] priceIncrements) {
			// set text
			if (priceIncrements.length == 0) {
				m_text.setText( null);
			}
			else {
				StringBuilder sb = new StringBuilder(256);
				DecimalFormat df = new DecimalFormat("#.#");
				df.setMaximumFractionDigits(340);

				sb.append("Market Rule Id: ").append(marketRuleId).append("\n");
				for (PriceIncrement priceIncrement : priceIncrements) {
					sb.append("Low Edge: ").append(df.format(priceIncrement.lowEdge())).append(", ")
					  .append("Increment: ").append(df.format(priceIncrement.increment())).append("\n");
				}
				m_text.setText( sb.toString());
			}
		}
	}
}
