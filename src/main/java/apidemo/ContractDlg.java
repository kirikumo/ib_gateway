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
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.SwingConstants;

import com.ib.client.Contract;

import apidemo.util.HtmlButton;


class ContractDlg extends JDialog {
	ContractPanel m_contractPanel;
    
    ContractDlg( JFrame f, Contract c) {
    	super( f, true);
    	
    	m_contractPanel = new ContractPanel( c);
    	
    	setLayout( new BorderLayout() );
    	
    	
    	HtmlButton ok = new HtmlButton( "OK") {
			@Override public void actionPerformed() {
				onOK();
			}
		};
		ok.setHorizontalAlignment(SwingConstants.CENTER);

		m_contractPanel.addKeyListener( new KeyListener() {
			@Override public void keyTyped(KeyEvent e) {
				System.out.println( "lkj");
			}
			
			@Override public void keyReleased(KeyEvent e) {
			}
			
			@Override public void keyPressed(KeyEvent e) {
			}
		});
    	
    	add( m_contractPanel);
    	add( ok, BorderLayout.SOUTH);
    	pack();
    }

	public void onOK() {
		m_contractPanel.onOK();
		setVisible( false);
	}
}
