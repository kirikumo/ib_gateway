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

import apidemo.util.UpperField;
import apidemo.util.VerticalPanel;

public class SecDefOptParamsReqDlg extends JDialog {
	private final UpperField m_idFld = new UpperField("0");
	private final UpperField m_underlyingSymbolFld = new UpperField();
	private final UpperField m_futFopExchangeFld = new UpperField();
	private final UpperField m_underlyingSecTypeFld = new UpperField();
	private final UpperField m_underlyingConIdFld = new UpperField();
	private int m_id;
	private String m_underlyingSymbol;
	private String m_futFopExchange;
	private String m_underlyingSecType;
	private int m_underlyingConId;
	private boolean m_isOk;
	
	SecDefOptParamsReqDlg(SampleFrame owner) {
		super(owner);
		
		VerticalPanel paramsPanel = new VerticalPanel();
		JButton ok = new JButton("OK");
		JButton cancel = new JButton("Cancel");
		
		ok.addActionListener(e -> onOK());
		cancel.addActionListener(e -> onCancel());
		
		paramsPanel.add("Req Id", m_idFld);			
		paramsPanel.add("Underlying symbol", m_underlyingSymbolFld);			
		paramsPanel.add("FUT-FOP exchange", m_futFopExchangeFld);			
		paramsPanel.add("Underlying security type", m_underlyingSecTypeFld);			
		paramsPanel.add("Underlying contract id", m_underlyingConIdFld);			
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
		m_id = m_idFld.getInt();
		m_underlyingSymbol = m_underlyingSymbolFld.getText();
		m_futFopExchange = m_futFopExchangeFld.getText();
		m_underlyingSecType = m_underlyingSecTypeFld.getText();
		m_underlyingConId = m_underlyingConIdFld.getInt();
		m_isOk = true;

		dispose();
	}
	
	public boolean isOK() {
		return m_isOk;
	}
	
	public int id() {
		return m_id;
	}

	public String underlyingSecType() {
		return m_underlyingSecType;
	}

	public String underlyingSymbol() {
		return m_underlyingSymbol;
	}

	public String futFopExchange() {
		return m_futFopExchange;
	}

	public int underlyingConId() {
		return m_underlyingConId;
	}
	
}
