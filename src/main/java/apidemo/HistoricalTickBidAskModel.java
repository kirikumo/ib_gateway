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

import com.ib.client.HistoricalTickBidAsk;

class HistoricalTickBidAskModel extends AbstractTableModel {

    private List<HistoricalTickBidAsk> m_rows;

    public HistoricalTickBidAskModel(List<HistoricalTickBidAsk> rows) {
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
        HistoricalTickBidAsk row = m_rows.get(rowIndex);
                
        switch (columnIndex) {
            case 0: return row.time();
            case 1: return row.priceBid();
            case 2: return row.priceAsk();
            case 3: return row.sizeBid();
            case 4: return row.sizeAsk();
            case 5: return row.tickAttribBidAsk().toString();
        }
        
        return null;
    }

    @Override
    public String getColumnName(int column) {
        switch (column) {
            case 0: return "Time";
            case 1: return "Price Bid";
            case 2: return "Price Ask";
            case 3: return "Size Bid";
            case 4: return "Size Ask";
            case 5: return "Bid/Ask Tick Attribs";
        }

        return super.getColumnName(column);
    }

}
