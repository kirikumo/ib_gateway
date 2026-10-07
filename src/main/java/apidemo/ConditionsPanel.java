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

import java.awt.FlowLayout;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.DefaultCellEditor;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;

import com.ib.client.ContractLookuper;
import com.ib.client.Order;
import com.ib.client.OrderCondition;
import com.ib.client.OrderConditionType;

import apidemo.util.HtmlButton;
import apidemo.util.TCombo;

public class ConditionsPanel extends OnOKPanel {
	/**
	 * 
	 */
	private final JDialog parentDlg;
	private final Order m_order;
	private final ConditionsModel m_conditionList;
	private final JTable m_conditions;
	private final TCombo<String> m_cancelOrder = new TCombo<>("Submit order", "Cancel order");
	private final JCheckBox m_ignoreRth = new JCheckBox("Allow condition to be satisfied and activate order outside of regular trading hours");
	private final JCheckBox m_includeOvernight = new JCheckBox("Include OVERNIGHT trading session");

	public ConditionsPanel(JDialog parentDlg, Order order, final ContractLookuper lookuper) {
		this.parentDlg = parentDlg;
		this.m_order = order;
		m_conditionList = new ConditionsModel(m_order.conditions(), lookuper);
		m_conditions = new JTable(m_conditionList);
		m_conditions.addMouseListener(new MouseListener() {

			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 1) {
					return;
				}

				if (m_conditions.getSelectedColumn() == 0) {
					ConditionDlg dlg = new ConditionDlg(m_order.conditions().get(m_conditions.getSelectedRow()), lookuper);                                

					dlg.setLocationRelativeTo(e.getComponent());
					dlg.setVisible(true);
				}
			}

			@Override public void mousePressed(MouseEvent e) { }

			@Override public void mouseReleased(MouseEvent e) { }

			@Override public void mouseEntered(MouseEvent e) { }

			@Override public void mouseExited(MouseEvent e) { }
		});
		
		TCombo<String> comboBox = new TCombo<>("and", "or");
		DefaultCellEditor editor = new DefaultCellEditor(comboBox);		

		m_conditions.getColumnModel().getColumn(1).setCellEditor(editor);

		add(new JScrollPane(m_conditions));
		
		JPanel buttons = new JPanel( new FlowLayout( FlowLayout.CENTER, 15, 5));
		
		m_cancelOrder.setSelectedIndex(m_order.conditionsCancelOrder() ? 1 : 0);
		m_ignoreRth.setSelected(m_order.conditionsIgnoreRth());
		m_includeOvernight.setSelected(m_order.conditionsIncludeOvernight());

		buttons.add(m_cancelOrder);
		buttons.add(m_ignoreRth);			
		buttons.add(m_includeOvernight);

		buttons.add(new HtmlButton("Add") { @Override
			protected void actionPerformed() {
				ConditionDlg dlg = new ConditionDlg(OrderCondition.create(OrderConditionType.Price), lookuper);
				
				dlg.setLocationRelativeTo(this.getParent());
				dlg.pack();
				dlg.setVisible(true);
				
				if (!dlg.isCanceled()) {
					m_order.conditions().add(dlg.condition());
					m_conditionList.fireTableDataChanged();
				}
			}
		});
		
		buttons.add(new HtmlButton("Remove") { @Override
			protected void actionPerformed() {
				int iRemove = m_conditions.getSelectedRow();
				
				if (iRemove >= 0) {
					m_order.conditions().remove(m_order.conditions().get(iRemove));
					m_conditionList.fireTableDataChanged();
				}
			}
		});
		
		add(buttons);
		this.parentDlg.pack();
	}
	
	public OrderCondition onOK() {
		m_order.conditionsCancelOrder(m_cancelOrder.getSelectedIndex() == 1);
		m_order.conditionsIgnoreRth(m_ignoreRth.isSelected());
		m_order.conditionsIncludeOvernight(m_includeOvernight.isSelected());

		return null;
	}
}