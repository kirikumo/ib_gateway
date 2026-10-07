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

import com.ib.client.MarginCondition;

public class MarginConditionPanel extends OperatorConditionPanel<MarginCondition> {
	
	MarginConditionPanel(MarginCondition condition) {
		super(condition);
		
		m_value.setText(condition().percent());
		
		add("Operator", m_operator);
		add("Cushion (%)", m_value);
	}
	
	public MarginCondition onOK() {
		super.onOK();
		condition().percent(m_value.getInt());
		
		return condition();
	}
}
