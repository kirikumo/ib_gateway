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
import java.awt.Dimension;
import java.awt.Frame;

import javax.swing.JButton;
import javax.swing.JDialog;

class FinancialAdvisorDlg extends JDialog {
    private static final int DIALOG_WIDTH = 500;
    private static final int EDITOR_HEIGHT = 240;
    private IBTextPanel groupTextEditor = new IBTextPanel("Groups", true) ;
    private IBTextPanel aliasTextEditor = new IBTextPanel("Aliases", true) ;
    String      groupsXML ;
    String      aliasesXML ;
    boolean 	m_rc = false;

    FinancialAdvisorDlg( Frame owner) {
        super( owner, "Financial Advisor", true);

        IBGridBagPanel editPanel = new IBGridBagPanel();

        editPanel.SetObjectPlacement( groupTextEditor,      0, 0 ) ;
        editPanel.SetObjectPlacement( aliasTextEditor,      0, 1 ) ;
        Dimension editPanelSizeDimension =
            new Dimension(DIALOG_WIDTH, 3 * EDITOR_HEIGHT);
        editPanel.setPreferredSize(editPanelSizeDimension) ;

        IBGridBagPanel buttonPanel = new IBGridBagPanel();
        JButton btnOk = new JButton("OK");
        buttonPanel.add(btnOk);
        JButton btnCancel = new JButton("Cancel");
        buttonPanel.add(btnCancel);

        // create action listeners
        btnOk.addActionListener(e -> onOk());
        btnCancel.addActionListener(e -> onCancel());

        //setTitle( "Financial Advisor");
        getContentPane().add( editPanel, BorderLayout.NORTH);
        getContentPane().add( buttonPanel, BorderLayout.CENTER);
        pack();
    }

    void receiveInitialXML(String p_groupsXML, String p_aliasesXML) {
        groupTextEditor.setTextDetabbed(p_groupsXML);
        aliasTextEditor.setTextDetabbed(p_aliasesXML);
    }

    void onOk() {
        m_rc = true;
        groupsXML = groupTextEditor.getText();
        aliasesXML = aliasTextEditor.getText();
        setVisible( false);
    }

    void onCancel() {
        m_rc = false;
        setVisible( false);
    }
}
