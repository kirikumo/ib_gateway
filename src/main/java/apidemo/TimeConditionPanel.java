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

import com.ib.client.TimeCondition;

public class TimeConditionPanel extends OperatorConditionPanel<TimeCondition> {

	TimeConditionPanel(TimeCondition condition) {
		super(condition);

		m_value.setText(condition().time());
		
		add("Operator", m_operator);
		add("Time", m_value);
	}
	
	@Override
	public TimeCondition onOK() {
		super.onOK();	
		condition().time(m_value.getText());
		
		return condition();
	}
}
