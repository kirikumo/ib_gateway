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

import com.ib.client.HistoricalTickLast;

class HistoricalTickLastModel extends AbstractTableModel {

    private List<HistoricalTickLast> m_rows;

    public HistoricalTickLastModel(List<HistoricalTickLast> rows) {
        m_rows = rows;
    }

    @Override
    public int getRowCount() {
        return m_rows.size();
    }

    @Override
    public int getColumnCount() {        
        return 6;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        HistoricalTickLast row = m_rows.get(rowIndex);
        
        switch (columnIndex) {
            case 0: return row.time();
            case 1: return row.price();
            case 2: return row.size();
            case 3: return row.exchange();
            case 4: return row.specialConditions();
            case 5: return row.tickAttribLast().toString();
        }
        return null;
    }

    @Override
    public String getColumnName(int column) { 
        switch (column) {
            case 0: return "Time";
            case 1: return "Price";
            case 2: return "Size";
            case 3: return "Exchange";
            case 4: return "Special conditions";
            case 5: return "Last Tick Attribs";
        }
        return super.getColumnName(column);
    }

}
