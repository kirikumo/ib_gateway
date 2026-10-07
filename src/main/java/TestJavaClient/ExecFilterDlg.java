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
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import com.ib.client.ExecutionFilter;

public class ExecFilterDlg extends JDialog {

	int m_reqId;
    ExecutionFilter	m_execFilter = new ExecutionFilter();
    public boolean 		m_rc;

    private JTextField 	m_reqIdTxt = new JTextField("0");
    private JTextField 	m_clientID = new JTextField("0");
    private JTextField 	m_acctCode = new JTextField();
    private JTextField 	m_time = new JTextField();
    private JTextField 	m_symbol = new JTextField();
    private JTextField 	m_secType = new JTextField();
    private JTextField 	m_exchange = new JTextField();
    private JTextField 	m_action = new JTextField();
    private JTextField 	m_lastNDays = new JTextField();
    private JTextField 	m_specificDates = new JTextField();

    ExecFilterDlg( JFrame owner) {
        super( owner, true);

        setTitle( "Execution Report Filter");

        // create extended order attributes panel
        JPanel execRptFilterPanel = new JPanel( new GridLayout( 0, 2, 7, 7) );
        execRptFilterPanel.setBorder( BorderFactory.createTitledBorder( "Filter Criteria") );
        execRptFilterPanel.add( new JLabel( "Request ID:") );
        execRptFilterPanel.add( m_reqIdTxt);
        execRptFilterPanel.add( new JLabel( "Client ID:") );
        execRptFilterPanel.add( m_clientID);
        execRptFilterPanel.add( new JLabel( "Account Code:") );
        execRptFilterPanel.add( m_acctCode);
        execRptFilterPanel.add( new JLabel( "Time :") );
        execRptFilterPanel.add( m_time);
        execRptFilterPanel.add( new JLabel( "Symbol :") );
        execRptFilterPanel.add( m_symbol);
        execRptFilterPanel.add( new JLabel( "SecType :") );
        execRptFilterPanel.add( m_secType);
        execRptFilterPanel.add( new JLabel( "Exchange :") );
        execRptFilterPanel.add( m_exchange);
        execRptFilterPanel.add( new JLabel( "Action :") );
        execRptFilterPanel.add( m_action);
        execRptFilterPanel.add( new JLabel( "Last N Days :") );
        execRptFilterPanel.add( m_lastNDays);
        execRptFilterPanel.add( new JLabel( "Specific Dates :") );
        execRptFilterPanel.add( m_specificDates);

        // create button panel
        JPanel buttonPanel = new JPanel();
        JButton btnOk = new JButton("OK");
        buttonPanel.add(btnOk);
        JButton btnCancel = new JButton("Cancel");
        buttonPanel.add(btnCancel);

        // create action listeners
        btnOk.addActionListener(e -> onOk());
        btnCancel.addActionListener(e -> onCancel());

        // create dlg box
        getContentPane().add( execRptFilterPanel, BorderLayout.CENTER);
        getContentPane().add( buttonPanel, BorderLayout.SOUTH);
        pack();
    }

    void onOk() {
        m_rc = false;

        try {

            m_reqId = Integer.parseInt( m_reqIdTxt.getText());

            // set extended order fields
            String clientId = m_clientID.getText();
            if(clientId.equals("")){
            	m_execFilter.clientId(0);
            }else{
            	m_execFilter.clientId(Integer.parseInt(clientId));
            }
            m_execFilter.acctCode(m_acctCode.getText());
            m_execFilter.time(m_time.getText());
            m_execFilter.symbol(m_symbol.getText());
            m_execFilter.secType(m_secType.getText());
            m_execFilter.exchange(m_exchange.getText());
            m_execFilter.side(m_action.getText());
            
            String lastNDays = m_lastNDays.getText();
            if (!lastNDays.isEmpty()) {
                m_execFilter.lastNDays(Integer.parseInt(lastNDays));
            }
            String specificDates = m_specificDates.getText();
            if (!specificDates.isEmpty()) {
                List<Integer> specificDatesList = Stream.of(specificDates.split(",")).map(Integer::parseInt).collect(Collectors.toList());
                m_execFilter.specificDates(specificDatesList);
            }
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
