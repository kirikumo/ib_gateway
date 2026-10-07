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
import java.awt.Frame;
import java.awt.GridLayout;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class LogConfigDlg extends JDialog {
    public static final int SYSTEM_LOG = 1;
    public static final int ERROR_LOG = 2;
    public static final int WARN_LOG = 3;
    public static final int INFO_LOG = 4;
    public static final int DETAIL_LOG = 5;

    private JComboBox<String> 	m_cmbServerLogLevels = new JComboBox<>();
    int 	m_serverLogLevel;
    boolean 	m_rc;

    LogConfigDlg( Frame owner) {
        super( owner, true);

        // create button panel
        JPanel buttonPanel = new JPanel();
        JButton btnOk = new JButton("OK");
        buttonPanel.add(btnOk);
        JButton btnCancel = new JButton("Cancel");
        buttonPanel.add(btnCancel);

        // create action listeners
        btnOk.addActionListener(e -> onOk());
        btnCancel.addActionListener(e -> onCancel());

        // create mid panel
        m_cmbServerLogLevels.addItem("System");
        m_cmbServerLogLevels.addItem("Error");
        m_cmbServerLogLevels.addItem("Warning");
        m_cmbServerLogLevels.addItem("Information");
        m_cmbServerLogLevels.addItem("Detail");

        JPanel midPanel = new JPanel();
        midPanel.setLayout( new GridLayout( 0, 2, 5, 5) );
        midPanel.add( new JLabel( "Log Level :") );
        midPanel.add( m_cmbServerLogLevels);

        // create dlg box
        getContentPane().add( midPanel, BorderLayout.NORTH);
        getContentPane().add( buttonPanel, BorderLayout.SOUTH);
        setTitle( "Log Configuration");
        pack();
    }

    void onOk() {
        // set server log Level
        m_serverLogLevel = m_cmbServerLogLevels.getSelectedIndex() + 1;
        m_rc = true;
        setVisible( false);
    }

    void onCancel() {
        m_rc = false;
        setVisible( false);
    }
}
