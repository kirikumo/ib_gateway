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

import com.ib.client.HistoricalTick;

class HistoricalTickModel extends AbstractTableModel {
    
    private List<HistoricalTick> m_rows;

    public HistoricalTickModel(List<HistoricalTick> rows) {
        m_rows = rows;
    }

    @Override
    public int getRowCount() {        
        return m_rows.size();
    }

    @Override
    public int getColumnCount() {
        return 3;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        HistoricalTick row = m_rows.get(rowIndex);
        
        switch (columnIndex) {
            case 0: return row.time();
            case 1: return row.price();
            case 2: return row.size();
        }
        
        return null;
    }

    @Override
    public String getColumnName(int column) {
        switch (column) {
            case 0: return "Time";
            case 1: return "Price";
            case 2: return "Size";
        }
        
        return super.getColumnName(column);
    }

}
