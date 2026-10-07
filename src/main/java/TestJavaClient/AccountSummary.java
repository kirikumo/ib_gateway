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


public class AccountSummary extends JDialog {

    public boolean m_rc;

    int m_reqId;
    String m_groupName;
    String m_tags;

    private JTextField 	m_reqIdTxt = new JTextField("0");
    private JTextField 	m_groupNameTxt = new JTextField("All");
    private JTextField 	m_tagsTxt = new JTextField("AccruedCash,BuyingPower,NetLiquidation");

    public AccountSummary( JFrame owner) {
        super( owner, true);

        setTitle( "Account Summary");

        // create account summary panel
        JPanel accountSummaryPanel = new JPanel( new GridLayout( 0, 2, 3, 3) );
        accountSummaryPanel.add( new JLabel( "Request ID:") );
        accountSummaryPanel.add( m_reqIdTxt);
        accountSummaryPanel.add( new JLabel( "Group Name:") );
        accountSummaryPanel.add( m_groupNameTxt);
        accountSummaryPanel.add( new JLabel( "Tags:") );
        accountSummaryPanel.add( m_tagsTxt);

        // create button panel
        JPanel buttonPanel = new JPanel();
        JButton okButton = new JButton("OK");
        buttonPanel.add(okButton);
        JButton cancelButton = new JButton("Cancel");
        buttonPanel.add(cancelButton);

        // create action listeners
        okButton.addActionListener(e -> onOk());
        cancelButton.addActionListener(e -> onCancel());

        // create dlg box
        getContentPane().add( accountSummaryPanel, BorderLayout.CENTER);
        getContentPane().add( buttonPanel, BorderLayout.SOUTH);
        pack();
    }

    void onOk() {
        m_rc = false;

        try {
            m_reqId = Integer.parseInt( m_reqIdTxt.getText());
            m_groupName = m_groupNameTxt.getText();
            m_tags = m_tagsTxt.getText();
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
