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

import java.awt.BorderLayout;
import java.awt.GridLayout;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

class MarketRuleDlg extends JDialog {
    private JTextField		m_marketRuleId = new JTextField("0");
    private boolean 		m_rc;
    
    int m_retMarketRuleId;

    boolean rc()            { return m_rc; }

    MarketRuleDlg(JFrame parent) {
        super(parent, true);

        // create button panel
        JPanel buttonPanel = new JPanel();
        JButton btnOk = new JButton("OK");
        buttonPanel.add(btnOk);
        JButton btnCancel = new JButton("Cancel");
        buttonPanel.add(btnCancel);

        // create action listeners
        btnOk.addActionListener(e -> onOk());
        btnCancel.addActionListener(e -> onCancel());
        
        // create mid summary panel
        JPanel midPanel = new JPanel( new GridLayout( 0, 1, 5, 5) );
        midPanel.add( new JLabel( "Market Rule Id") );
        midPanel.add( m_marketRuleId);

        // create dlg box
        getContentPane().add( midPanel, BorderLayout.CENTER);
        getContentPane().add( buttonPanel, BorderLayout.SOUTH);
        setTitle( "Market Rule Request");
        pack();    }

    void onOk() {
        m_rc = false;

        try {
            m_retMarketRuleId = Integer.parseInt( m_marketRuleId.getText());
        }
        catch( Exception e) {
            Main.inform( this, "Error - " + e);
            return;
        }

        m_rc = true;
        setVisible( false);
    }

    void onCancel() {
        m_rc = false;
        setVisible( false);
    }
}
