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

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JTextField;

import apidemo.util.UpperField;
import apidemo.util.VerticalPanel;

public class SmartComponentsParamsReqDlg extends JDialog {

	private int m_id;
	private String m_BBOExchange;
	private boolean m_isOk;
	
	final private UpperField m_idFld = new UpperField("0");
	final private JTextField m_BBOExchangeFld = new JTextField();


	SmartComponentsParamsReqDlg(SampleFrame owner) {
		super(owner);
		
		VerticalPanel paramsPanel = new VerticalPanel();
		JButton ok = new JButton("OK");
		JButton cancel = new JButton("Cancel");
		
		ok.addActionListener(e -> onOK());
		cancel.addActionListener(e -> onCancel());
				
		paramsPanel.add("Req Id", m_idFld);			
		paramsPanel.add("BBO Exchange", m_BBOExchangeFld);			
		paramsPanel.add(ok);
		paramsPanel.add(cancel);
		setLayout(new BorderLayout());
		add(paramsPanel, BorderLayout.NORTH);
		pack();
	}

	protected void onCancel() {
		m_isOk = false;
		
		dispose();
	}

	protected void onOK() {
		m_BBOExchange = m_BBOExchangeFld.getText();
		m_id = m_idFld.getInt();
		m_isOk = true;		

		dispose();
	}
	
	public boolean isOK() {
		return m_isOk;
	}
	
	String BBOExchange() {
		return m_BBOExchange;
	}
	
	public int id() {
		return m_id;
	}
}
