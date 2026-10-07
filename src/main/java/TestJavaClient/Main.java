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

import java.awt.Component;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public class Main {

    // This method is called to start the application
    public static void main (String args[]) {
        SampleFrame sampleFrame = new SampleFrame();
        sampleFrame.setVisible(true);
    }

    public static void inform( final Component parent, final String str) {
        if( SwingUtilities.isEventDispatchThread() ) {
        	showMsg( parent, str, JOptionPane.INFORMATION_MESSAGE);
        } else {
            SwingUtilities.invokeLater(() -> showMsg( parent, str, JOptionPane.INFORMATION_MESSAGE));
        }
    }

    private static void showMsg( Component parent, String str, int type) {
        // this function pops up a dlg box displaying a message
        JOptionPane.showMessageDialog( parent, str, "IB Java Test Client", type);
    }
}
