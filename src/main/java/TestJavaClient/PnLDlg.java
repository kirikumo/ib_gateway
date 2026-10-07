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

import javax.swing.*;

public class PnLDlg extends JDialogBox {

    int m_reqId;
    String m_account;
    String m_modelCode;
    
    private JTextField m_reqIdField = new JTextField();
    private JTextField m_accountField = new JTextField();
    private JTextField m_modelCodeField = new JTextField();

    protected JPanel m_editsPanel = new JPanel(new GridLayout(0, 1));

    public PnLDlg(JFrame parent) {
        super(parent);
        
        m_editsPanel.add(new JLabel("Req id"));
        m_editsPanel.add(m_reqIdField);
        
        m_editsPanel.add(new JLabel("Account"));
        m_editsPanel.add(m_accountField);

        m_editsPanel.add(new JLabel("Model code"));
        m_editsPanel.add(m_modelCodeField);
        
        getContentPane().add(m_editsPanel);
        pack();
    }
    
    @Override
    protected void onOk() {
        try {
            m_reqId = Integer.parseInt(m_reqIdField.getText());
            m_account = m_accountField.getText();
            m_modelCode = m_modelCodeField.getText();
        } finally {        
            super.onOk();
        }
    }

}
