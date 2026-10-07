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

import com.ib.client.ContractLookuper;
import com.ib.client.PercentChangeCondition;

public class PercentConditionPanel extends ContractConditionPanel<PercentChangeCondition> {
	
	PercentConditionPanel(PercentChangeCondition condition, ContractLookuper lookuper) {
		super(condition, lookuper);
		
		m_value.setText(condition().changePercent());
		
		add("Operator", m_operator);
		add("Percentage Change", m_value);
	}
	
	
	public PercentChangeCondition onOK() {
		super.onOK();
		condition().changePercent(m_value.getDouble());
		
		return condition();
	}
}
