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

import com.ib.client.ExecutionCondition;
import com.ib.client.OrderCondition;

import apidemo.util.UpperField;

public class TradeConditionPanel extends OnOKPanel {
	private ExecutionCondition m_condition;
	private final UpperField m_secType = new UpperField();
	private final UpperField m_exchange =  new UpperField();
	private final UpperField m_symbol = new UpperField();
	
	public TradeConditionPanel(ExecutionCondition condition) {
		m_condition = condition;
		
		m_secType.setText(m_condition.secType());
		m_exchange.setText(m_condition.exchange());
		m_symbol.setText(m_condition.symbol());
		
		add("Underlying", m_symbol);
		add("Exchange", m_exchange);
		add("Type", m_secType);
	}
	
	public OrderCondition onOK() {
		m_condition.symbol(m_symbol.getText());
		m_condition.exchange(m_exchange.getText());
		m_condition.secType(m_secType.getText());
		
		return m_condition;
	}
}
