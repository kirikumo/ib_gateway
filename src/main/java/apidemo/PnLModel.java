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

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

import javax.swing.table.AbstractTableModel;

public class PnLModel extends AbstractTableModel {

    private class PnLItem {
        
        public PnLItem(double dailyPnL, double unrealizedPnL, double realizedPnL) {
            m_dailyPnL = dailyPnL;
            m_unrealizedPnL = unrealizedPnL;
            m_realizedPnL = realizedPnL;
        }
        
        public double dailyPnL() {
            return m_dailyPnL;
        }

        public double unrealizedPnL() {
            return m_unrealizedPnL;
        }        
        
        public double realizedPnL() {
            return m_realizedPnL;
        }

        private double m_dailyPnL;
        private double m_unrealizedPnL;
        private double m_realizedPnL;
        
    }
    
    private List<PnLItem> m_rows = new ArrayList<>();
    private static Dictionary<Integer, String> columnNames = new Hashtable<>();
    
    static {
        columnNames.put(0, "Daily Pnl");
        columnNames.put(1, "Unrealized PnL");
        columnNames.put(2, "Realized PnL");       
    }
    
    @Override
    public String getColumnName(int column) {
        return columnNames.get(column);
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
        PnLItem item = m_rows.get(rowIndex);
        
        switch (columnIndex) {
        case 0:
            return item.dailyPnL();
            
        case 1:
            return item.unrealizedPnL();
            
        case 2:
            return item.realizedPnL();
        }
        
        return null;
    }

    public void addRow(double dailyPnL, double unrealizedPnL, double realizedPnL) {
        m_rows.add(new PnLItem(dailyPnL, unrealizedPnL, realizedPnL));
        
        fireTableDataChanged();
    }

}
