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
import java.awt.Color;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JRadioButton;

public class NewsBulletinDlg extends JDialog {

    private JRadioButton 	m_btnAllMsgs = new JRadioButton( "receive all the current day's messages and any new messages.");

    public boolean 		m_rc;
    public boolean 		m_subscribe;
    public boolean 		m_allMsgs;

    NewsBulletinDlg(SampleFrame parent) {
        super(parent, "IB News Bulletin Subscription", true);

        ButtonGroup btnGroup = new ButtonGroup();
        JRadioButton btnNewMsgs = new JRadioButton("receive new messages only.");
        btnGroup.add(btnNewMsgs);
        btnGroup.add( m_btnAllMsgs);
        btnNewMsgs.setSelected( true);

        // register button listeners
        JButton btnSubscribe = new JButton("Subscribe");
        btnSubscribe.addActionListener(e -> onSubscribe());
        JButton btnUnsubscribe = new JButton("Unsubscribe");
        btnUnsubscribe.addActionListener(e -> onUnSubscribe());
        JButton btnClose = new JButton("Close");
        btnClose.addActionListener(e -> onClose());

        IBGridBagPanel subscriptionTypePanel = new IBGridBagPanel();
        subscriptionTypePanel.setBorder( BorderFactory.createLineBorder( Color.BLACK));
        JLabel optionTypeLabel = new JLabel("When subscribing to IB news bulletins you have 2 options:");
        subscriptionTypePanel.SetObjectPlacement(optionTypeLabel, 0, 0);
        subscriptionTypePanel.SetObjectPlacement(btnNewMsgs,    0, 1);
        subscriptionTypePanel.SetObjectPlacement(m_btnAllMsgs,    0, 2);

        IBGridBagPanel mainPanel = new IBGridBagPanel();
        mainPanel.SetObjectPlacement(subscriptionTypePanel,     0, 0, 4, 1);
        mainPanel.SetObjectPlacement(btnSubscribe,              0, 1);
        mainPanel.SetObjectPlacement(btnUnsubscribe,            1, 1);
        mainPanel.SetObjectPlacement(btnClose,                  3, 1);

        getContentPane().add(mainPanel, BorderLayout.CENTER);
        setSize( 460, 160);
    }

    private void onSubscribe() {
        m_rc = true;
        m_subscribe = true;
        m_allMsgs = m_btnAllMsgs.isSelected();
        setVisible( false);
    }

    private void onUnSubscribe() {
        m_rc = true;
        m_subscribe = false;
        m_allMsgs = false;
        setVisible( false);
    }

    private void onClose() {
        m_rc = false;
        setVisible( false);
    }
}
