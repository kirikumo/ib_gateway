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

import java.util.List;

import javax.swing.table.AbstractTableModel;

import com.ib.client.ContractCondition;
import com.ib.client.ContractLookuper;
import com.ib.client.OrderCondition;

public class ConditionsModel extends AbstractTableModel {
	
	@Override
	public void setValueAt(Object val, int row, int col) {
		if (col == 0) {
			super.setValueAt(val, row, col);
			
			return;
		}
		
		m_conditions.get(row).conjunctionConnection("and".equals(val.toString()));
	}

	@Override
	public boolean isCellEditable(int row, int col) {		
		return col == 1;
	}

	List<OrderCondition> m_conditions;
	ContractLookuper m_lookuper;
	
	public ConditionsModel(List<OrderCondition> conditions, ContractLookuper lookuper) {
		m_conditions = conditions;
		m_lookuper = lookuper;
	}

	@Override
	public int getColumnCount() {
		return 2;
	}

	@Override
	public int getRowCount() {		
		return m_conditions.size();
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		if (columnIndex == 0) {
			OrderCondition orderCondition = m_conditions.get(rowIndex);
			
			return orderCondition instanceof ContractCondition ?
					((ContractCondition)orderCondition).toString(m_lookuper) : 
					orderCondition.toString();
		}
		
		return m_conditions.get(rowIndex).conjunctionConnection() ? "and" : "or";
	}

	@Override
	public String getColumnName(int column) {
		switch (column) {
		case 0:
			return "Description";
			
		case 1:
			return "Logic";
		}
		
		return null;
	}

}
