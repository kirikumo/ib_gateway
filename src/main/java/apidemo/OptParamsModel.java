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

import java.util.Set;

import javax.swing.table.AbstractTableModel;

public class OptParamsModel extends AbstractTableModel {

	String[] m_expirations;
	Double[] m_strikes;
	
	public OptParamsModel(Set<String> expirations, Set<Double> strikes) {
		expirations.toArray(m_expirations = new String[expirations.size()]);
		strikes.toArray(m_strikes = new Double[strikes.size()]);		
	}	
	
	@Override
	public int getRowCount() {
		return Math.max(m_expirations.length, m_strikes.length);
	}

	@Override
	public int getColumnCount() {
		return 2;
	}

	@Override
	public String getColumnName(int column) {
		switch (column) {
		case 0:
			return "Expirations";
			
		case 1:
			return "Strikes";
		}
		
		return super.getColumnName(column);
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		switch (columnIndex) {
		case 0:
			return rowIndex < m_expirations.length ? m_expirations[rowIndex] : null;

		case 1:
			return rowIndex < m_strikes.length ? m_strikes[rowIndex] : null;

		default:
			return null;
		}
	}

}
