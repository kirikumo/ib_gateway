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

public class WSHMetaDataModel extends AbstractTableModel {

private class EventDataItem {
        
        public EventDataItem(String dataJson) {
            m_dataJson = dataJson;
        }

        public String dataJSON() {
        	return m_dataJson;
        }

        private String m_dataJson;
        
    }
    
    private List<EventDataItem> m_rows = new ArrayList<>();
    private static String[] columnNames = new String[] { "Data JSON" };
    
    @Override
    public String getColumnName(int column) {
        return columnNames[column];
    }
    
    @Override
    public int getRowCount() {
        return m_rows.size();
    }

    @Override
    public int getColumnCount() {
        return columnNames.length;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        EventDataItem item = m_rows.get(rowIndex);
        
        switch (columnIndex) {
        case 0:
            return item.dataJSON();
        }
        
        return null;
    }

    public void addRow(String dataJson) {
        m_rows.add(new EventDataItem(dataJson));
        
        fireTableDataChanged();
    }

}
