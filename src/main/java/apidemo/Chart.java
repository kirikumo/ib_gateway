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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.util.List;

import javax.swing.JComponent;

import com.ib.controller.Bar;

public class Chart extends JComponent {
	private static final int width = 5;
	private int height;
	private double min;
	private double max;
	private final List<Bar> m_rows;
	private double m_current = 118;
	
	public void current( double v) { m_current = v; }
	
	public Chart(List<Bar> rows) {
		m_rows = rows;
	}
	
	@Override protected void paintComponent(Graphics g) {
		height = getHeight();
		min = getMin();
		max = getMax();
	
		int x = 1;
		for (Bar bar : m_rows) {
			int high = getY( bar.high() );
			int low = getY( bar.low() ); 
			int open = getY( bar.open() );
			int close = getY( bar.close() );

			// draw high/low line
			g.setColor( Color.black);
			g.drawLine(x + 1, high, x + 1, low);
			
			if (bar.close() > bar.open() ) {
				g.setColor( Color.green);
				g.fillRect(x, close, 3, open - close);
			}
			else {
				g.setColor( Color.red);
				g.fillRect(x, open, 3, close - open);
			}

			x += width;
		}

		// draw price line
		g.setColor( Color.black);
		int y = getY( m_current);
		g.drawLine( 0, y, m_rows.size() * width, y);
	}
	
	/** Convert bar value to y coordinate. */
	private int getY( double v) {
		double span = max - min;
		double pct = (v - min) / span;
		double val = pct * height + .5;
		return height - (int)val;
	}
	
	@Override public Dimension getPreferredSize() {// why on main screen 1 is okay but not here?
		return new Dimension( m_rows.size() * width, 100);
	}
	
	private double getMin() {
		double min = Double.MAX_VALUE;
		for( Bar bar : m_rows) {
			min = Math.min( min, bar.low() );
		}
		return min;
	}
	
	private double getMax() {
		double max = 0;
		for( Bar bar : m_rows) {
			max = Math.max( max, bar.high() );
		}
		return max;
	}
}
