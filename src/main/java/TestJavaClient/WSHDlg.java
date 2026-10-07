/*
 * Java TestJavaClient
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

package TestJavaClient;

import java.awt.GridLayout;

import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import com.ib.client.WshEventData;

public class WSHDlg extends JDialogBox {

    int m_reqId;
    WshEventData m_wshEventData;
    boolean m_isWshEventDlg;

    private JTextField m_reqIdField = new JTextField();

    protected JPanel m_editsPanel = new JPanel(new GridLayout(0, 1));

    private JTextField m_conIdField = new JTextField();
    private JTextField m_filterField = new JTextField();
    private JCheckBox m_fillWatchlistCheckbox = new JCheckBox("Fill Watchlist", false);
    private JCheckBox m_fillPortfolioCheckbox = new JCheckBox("Fill Portfolio", false);
    private JCheckBox m_fillCompetitorsCheckbox = new JCheckBox("Fill Competitors", false);
    private JTextField m_startDateField = new JTextField();
    private JTextField m_endDateField = new JTextField();
    private JTextField m_totalLimitField = new JTextField();

    public WSHDlg(JFrame parent, boolean isWshEventDlg) {
        super(parent);

        m_isWshEventDlg = isWshEventDlg;
        m_editsPanel.add(new JLabel("Req id"));
        m_editsPanel.add(m_reqIdField);

        if (m_isWshEventDlg) {
            m_editsPanel.add(new JLabel("Con Id"));
            m_editsPanel.add(m_conIdField);
            m_editsPanel.add(new JLabel("Filter"));
            m_editsPanel.add(m_filterField);
            m_editsPanel.add(m_fillWatchlistCheckbox);
            m_editsPanel.add(m_fillPortfolioCheckbox);
            m_editsPanel.add(m_fillCompetitorsCheckbox);
            m_editsPanel.add(new JLabel("Start Date"));
            m_editsPanel.add(m_startDateField);
            m_editsPanel.add(new JLabel("End Date"));
            m_editsPanel.add(m_endDateField);
            m_editsPanel.add(new JLabel("Total Limit"));
            m_editsPanel.add(m_totalLimitField);
        }

        getContentPane().add(m_editsPanel);
        pack();
    }

    @Override
    protected void onOk() {
        try {
            m_reqId = m_reqIdField.getText().length() > 0 ? Integer.parseInt(m_reqIdField.getText()) : 0;
            if (m_isWshEventDlg) {
                int conId = m_conIdField.getText().length() > 0 ? Integer.parseInt(m_conIdField.getText()) : Integer.MAX_VALUE;
                int totalLimit = m_totalLimitField.getText().length() > 0 ? Integer.parseInt(m_totalLimitField.getText()) : Integer.MAX_VALUE;
                m_wshEventData = conId != Integer.MAX_VALUE ? 
                        new WshEventData(conId, m_fillWatchlistCheckbox.isSelected(), 
                            m_fillPortfolioCheckbox.isSelected(), m_fillCompetitorsCheckbox.isSelected(),
                            m_startDateField.getText(), m_endDateField.getText(), totalLimit) : 
                        new WshEventData(m_filterField.getText(), m_fillWatchlistCheckbox.isSelected(), 
                            m_fillPortfolioCheckbox.isSelected(), m_fillCompetitorsCheckbox.isSelected(),
                            m_startDateField.getText(), m_endDateField.getText(), totalLimit);
            }

        } finally {
            super.onOk();
        }
    }

}
