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

import java.awt.Color;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

class FAAllocationInfoDlg extends JDialog {
    private IBGridBagPanel mainJPanel = new IBGridBagPanel();
    private IBGridBagPanel faGroupJPanel = new IBGridBagPanel();

    private JLabel m_groupLabel = new JLabel("Group");
    private JLabel m_methodLabel = new JLabel("Method");
    private JLabel m_percentageLabel = new JLabel("Percentage");

    private JTextField m_groupTextField = new JTextField(20);
    private JTextField m_methodTextField = new JTextField(20);
    private JTextField m_percentageTextField = new JTextField(20);

    private JButton m_okButton = new JButton("OK");
    private JButton m_closeButton = new JButton("Close");

    private OrderDlg m_parent;

    FAAllocationInfoDlg(OrderDlg dlg) {
        super(dlg, false);
        m_parent = dlg;
        try {
            jbInit();
            pack();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void jbInit() {
        Color etchedColor = new Color(148, 145, 140);
        Border border1 = BorderFactory.createEtchedBorder(Color.white, etchedColor);
        TitledBorder titledBorder1 = new TitledBorder(border1, "Group");

        faGroupJPanel.setBorder(titledBorder1);

        faGroupJPanel.SetObjectPlacement(m_groupLabel,           0, 0);
        faGroupJPanel.SetObjectPlacement(m_groupTextField,       1, 0);
        faGroupJPanel.SetObjectPlacement(m_methodLabel,          0, 1);
        faGroupJPanel.SetObjectPlacement(m_methodTextField,      1, 1);
        faGroupJPanel.SetObjectPlacement(m_percentageLabel,      0, 2);
        faGroupJPanel.SetObjectPlacement(m_percentageTextField,  1, 2);

        mainJPanel.SetObjectPlacement(faGroupJPanel,   0, 0, 4, 1);
        mainJPanel.SetObjectPlacement(m_okButton,      1, 2, 1, 1);
        mainJPanel.SetObjectPlacement(m_closeButton,   2, 2, 1, 1);

        setTitle("FA Allocation Info");
        getContentPane().add(mainJPanel);
        setSize( 600, 300);


        m_okButton.addActionListener(e -> onOk());
        m_closeButton.addActionListener(e -> onClose());
    }

    void onOk() {
        m_parent.faGroup(m_groupTextField.getText().trim());
        m_parent.faMethod(m_methodTextField.getText().trim());
        m_parent.faPercentage(m_percentageTextField.getText().trim());
        dispose();
    }

    void onClose() {
        dispose();
    }
}
