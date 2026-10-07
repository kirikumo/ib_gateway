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
import javax.swing.JOptionPane;

import com.google.protobuf.TextFormat;
import com.google.protobuf.TextFormat.ParseException;
import com.ib.client.protobuf.UpdateConfigRequestProto;

class ConfigDlg extends JDialog {
    private static final int DIALOG_WIDTH = 500;
    private static final int EDITOR_HEIGHT = 240;
    private IBTextPanel configTextEditor = new IBTextPanel("Config", true) ;
    UpdateConfigRequestProto.UpdateConfigRequest updateConfigRequestProto;
    boolean m_rc = false;

    ConfigDlg(Frame owner) {
        super(owner, "Config", true);

        IBGridBagPanel editPanel = new IBGridBagPanel();

        editPanel.SetObjectPlacement(configTextEditor, 0, 0) ;
        Dimension editPanelSizeDimension = new Dimension(DIALOG_WIDTH, 3 * EDITOR_HEIGHT);
        editPanel.setPreferredSize(editPanelSizeDimension) ;

        IBGridBagPanel buttonPanel = new IBGridBagPanel();
        JButton btnUpdateConfig = new JButton("Update Config");
        buttonPanel.add(btnUpdateConfig);
        JButton btnClose = new JButton("Close");
        buttonPanel.add(btnClose);

        // create action listeners
        btnUpdateConfig.addActionListener(e -> onUpdateConfig());
        btnClose.addActionListener(e -> onClose());

        getContentPane().add(editPanel, BorderLayout.NORTH);
        getContentPane().add(buttonPanel, BorderLayout.CENTER);
        pack();
    }

    void setConfigResponse(String configResponse) {
        configTextEditor.setTextDetabbed(configResponse);
    }

    void onUpdateConfig() {
        m_rc = true;
        String updateConfigText = configTextEditor.getText();

        if (updateConfigText != null && !updateConfigText.isEmpty()) {
            UpdateConfigRequestProto.UpdateConfigRequest.Builder updateConfigRequestBuilder = UpdateConfigRequestProto.UpdateConfigRequest.newBuilder();
            try {
                TextFormat.merge(updateConfigText, updateConfigRequestBuilder);
            } catch (ParseException e) {
                JOptionPane.showMessageDialog(null, "Cannot parse update config text");
                return;
            }
            if (!updateConfigRequestBuilder.hasReqId()) updateConfigRequestBuilder.setReqId(0);
            updateConfigRequestProto = updateConfigRequestBuilder.build();
        }
        setVisible(false);
    }

    void onClose() {
        m_rc = false;
        setVisible(false);
    }
}
