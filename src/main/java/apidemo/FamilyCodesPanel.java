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

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

import com.ib.client.FamilyCode;
import com.ib.controller.ApiController.IFamilyCodesHandler;

import apidemo.AccountInfoPanel.Table;
import apidemo.util.HtmlButton;
import apidemo.util.NewTabbedPanel.NewTabPanel;
import apidemo.util.VerticalPanel;


public class FamilyCodesPanel extends NewTabPanel {
	private FamilyCodesModel m_model = new FamilyCodesModel();

	FamilyCodesPanel() {
		HtmlButton requestFamilyCodesButton = new HtmlButton( "Request Family Codes") {
			protected void actionPerformed() {
				requestFamilyCodes();
			}
		};

		HtmlButton clearFamilyCodesButton = new HtmlButton( "Clear Family Codes") {
			protected void actionPerformed() {
				clearFamilyCodes();
			}
		};

		JPanel buts = new VerticalPanel();
		buts.add( requestFamilyCodesButton);
		buts.add( clearFamilyCodesButton);

		JTable table = new Table( m_model, 2);
		JScrollPane scroll = new JScrollPane( table);

		setLayout( new BorderLayout() );
		add( scroll);
		add( buts, BorderLayout.EAST);
	}

	/** Called when the tab is first visited. */
	@Override public void activated() { /* noop */ }

	/** Called when the tab is closed by clicking the X. */
	@Override public void closed() {
		clearFamilyCodes();
	}

	private void requestFamilyCodes() {
		ApiDemo.INSTANCE.controller().reqFamilyCodes( m_model);
	}

	private void clearFamilyCodes() {
		m_model.clear();
	}

	private class FamilyCodesModel extends AbstractTableModel implements IFamilyCodesHandler {
		List<FamilyCodeRow> m_list = new ArrayList<>();

		@Override public void familyCodes(FamilyCode[] familyCodes) {
			for (FamilyCode familyCode : familyCodes){
				FamilyCodeRow row = new FamilyCodeRow();
				m_list.add( row);
				row.update(familyCode.accountID(), familyCode.familyCodeStr());
			}
			m_model.fireTableDataChanged();
		}

		public void clear() {
			m_list.clear();
			fireTableDataChanged();
		}

		@Override public int getRowCount() {
			return m_list.size();
		}

		@Override public int getColumnCount() {
			return 2;
		}

		@Override public String getColumnName(int col) {
			switch( col) {
				case 0: return "Account ID";
				case 1: return "Family Code";
				default: return null;
			}
		}

		@Override public Object getValueAt(int rowIn, int col) {
			FamilyCodeRow row = m_list.get( rowIn);
			
			switch( col) {
				case 0: return row.m_accountID;
				case 1: return row.m_familyCodeStr;
				default: return null;
			}
		}

	}

	private static class FamilyCodeRow {
		String m_accountID;
		String m_familyCodeStr;

		void update(String accountID, String familyCodeStr) {
			m_accountID = accountID;
			m_familyCodeStr = familyCodeStr;
		}
	}
}
