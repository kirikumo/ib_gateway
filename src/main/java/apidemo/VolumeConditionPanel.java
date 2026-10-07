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
import com.ib.client.VolumeCondition;

public class VolumeConditionPanel extends ContractConditionPanel<VolumeCondition> {

	VolumeConditionPanel(VolumeCondition condition, ContractLookuper lookuper) {
		super(condition, lookuper);
		
		m_value.setText(condition().volume());
		
		add("Operator", m_operator);
		add("Volume", m_value);
	}

	@Override
	public VolumeCondition onOK() {
		super.onOK();
		
		condition().volume(m_value.getInt());
		
		return condition();
	}
}
