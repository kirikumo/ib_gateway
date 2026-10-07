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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;


public class VerticalPanel extends JPanel {
	private static class FlowPanel extends JPanel {
		FlowPanel( Component[] comps) {
			setLayout( new FlowLayout( FlowLayout.LEFT, 5, 2) );
			for( Component comp : comps) {
				add( comp);
			}
			setAlignmentX(0);
		}
		
		@Override public Dimension getMaximumSize() {
			return super.getPreferredSize();
		}

		int widAt(int index) {
			return getComponentCount() > index ? getComponent( index).getPreferredSize().width : 0;
		}

		void widAt(int index, int w) {
			if (getComponentCount() <= index) {
				return;
			}
			Dimension d = getComponent( index).getPreferredSize();
			d.width = w;
			getComponent( index).setPreferredSize( d);
		}
	}

	public VerticalPanel() {
		setLayout( new BoxLayout( this, BoxLayout.Y_AXIS));
	}

	@Override public Component add(Component comp) {
		add( new Component[] { comp } );
		return comp;
	}
	
	@Override public Component add(String str, Component comp) {
		add( new Component[] { new JLabel( str), comp } );
		return null;
	}
	
	public void add( String str, Component... cs) {
		Component[] ar = new Component[cs.length + 1];
		ar[0] = new JLabel( str);
		System.arraycopy(cs, 0, ar, 1, cs.length);
		add( ar);
	}

	public void add( Component... comps) {
		add( -1, comps);
	}
	
	@Override public Component add(Component comp, int index) {
		add( index, comp);
		return null;
	}
	
	public void add( int index, Component... comps) {
		super.add( new FlowPanel( comps), index);

		recalculateChildSizes();
	}

	private void recalculateChildSizes() {
		int maxCols = 0;
		for (int i = 0; i < getComponentCount(); i++) {
			FlowPanel comp = (FlowPanel)getComponent( i);
			maxCols = Math.max( comp.getComponentCount(), maxCols);
		}

		for (int col = 0; col < maxCols; col++) {
			int max = 0;
			for (int i = 0; i < getComponentCount(); i++) {
				FlowPanel comp = (FlowPanel)getComponent( i);
				max = Math.max( comp.widAt( col), max);
			}
			for (int i = 0; i < getComponentCount(); i++) {
				FlowPanel comp = (FlowPanel)getComponent( i);
				comp.widAt( col, max);
			}
		}
	}

	public void add(String str, Component comp, int index) {
		add( index, new JLabel( str), comp);				
	}
	
	@Override public void add(Component comp, Object constraints) {
		throw new RuntimeException(); // not valid
	}
	
	@Override public void add(Component comp, Object constraints, int index) {
		throw new RuntimeException(); // not valid
	}
	

	public static class HorzPanel extends JPanel {
		public HorzPanel() {
			setLayout( new BoxLayout( this, BoxLayout.X_AXIS));
		}
		
		public void add(JComponent comp) {
			comp.setAlignmentY(0);
			super.add( comp);
		}
	}

	public static class BorderPanel extends JPanel {
		public BorderPanel() {
			setLayout( new BorderLayout() );
		}
	}
	
	public static class StackPanel extends JPanel {
		public StackPanel() {
			setLayout( new BoxLayout( this, BoxLayout.Y_AXIS));
		}

		public void add(JComponent comp) {
			comp.setAlignmentX( 0);
			super.add( comp);
		}
		
		@Override public Dimension getMaximumSize() {
			return super.getPreferredSize();
		}
	}	
}
