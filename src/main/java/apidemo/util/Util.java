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

package apidemo.util;

import java.awt.FontMetrics;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

public class Util {
	private static final int BUF = 14;
	private static final int MAX = 300;
	
	/** Resize all columns in the table to fit widest row including header. */ 
	public static void resizeColumns( JTable table) {
		if (table.getGraphics() == null) {
			return;
		}
		
		DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
		FontMetrics fm = table.getFontMetrics( renderer.getFont() );

		TableColumnModel mod = table.getColumnModel();
		for (int iCol = 0; iCol < mod.getColumnCount(); iCol++) {
			TableColumn col = mod.getColumn( iCol);
			
			int max = col.getPreferredWidth() - BUF;
			
			String header = table.getModel().getColumnName( iCol);
			if (header != null) {
				max = Math.max( max, fm.stringWidth( header) );
			}
			
			for (int iRow = 0; iRow < table.getModel().getRowCount(); iRow++) {
				Object obj = table.getModel().getValueAt(iRow, iCol);
				String str = obj == null ? "" : obj.toString();
				max = Math.max( max, fm.stringWidth( str) );
			}

			col.setPreferredWidth( max + BUF);
			col.setMaxWidth( MAX);
		}
		table.revalidate();
		table.repaint();
	}
	
	/** Configure dialog to close when Esc is pressed. */
	public static void closeOnEsc( final JDialog dlg) {
        dlg.getRootPane().getActionMap().put( "Cancel", new AbstractAction() {
        	public void actionPerformed(ActionEvent e) {
        		dlg.dispose();
        	}
        });

        dlg.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put( KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "Cancel");
	}

	public static void sleep( int ms) {
		try {
			Thread.sleep( ms);
		} catch (InterruptedException e) {
			e.printStackTrace();
			Thread.currentThread().interrupt();
		}
	}
}
