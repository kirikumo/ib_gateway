/*
 * Java ApiDemo
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

package apidemo;

import java.awt.event.MouseEvent;

import com.ib.client.ContractLookuper;

import apidemo.util.HtmlButton;

public abstract class ContractLookupButton extends HtmlButton {

	final ContractSearchDlg m_contractSearchdlg;
			
	public ContractLookupButton(int conId, String exchange, ContractLookuper lookuper) {
		super("");
		
		m_contractSearchdlg = new ContractSearchDlg(conId, exchange, lookuper);
		
		setText(m_contractSearchdlg.refContract() == null ? "search..." : m_contractSearchdlg.refContract());
	}
	
	@Override
	protected void onClicked(MouseEvent e) {		
		m_contractSearchdlg.setLocationRelativeTo(this.getParent());
		m_contractSearchdlg.setVisible(true);
				
		setText(m_contractSearchdlg.refContract() == null ? "search..." : m_contractSearchdlg.refContract());
		actionPerformed();
		actionPerformed(m_contractSearchdlg.refConId(), m_contractSearchdlg.refExchId());
	}

	protected abstract void actionPerformed(int refConId, String refExchId);
}
