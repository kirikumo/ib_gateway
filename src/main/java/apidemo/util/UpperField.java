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

import javax.swing.JTextField;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;

public class UpperField extends JTextField {
	int m_ival;
	double m_dval;
	
	public UpperField() {
		this( null);
	}
	
	public UpperField(boolean allowLowerCase) {
		this( null, allowLowerCase);
	}

	public UpperField( int i) {
		this( null, i, false);
	}

	public UpperField( int i, boolean allowLowerCase) {
		this( null, i, allowLowerCase);
	}
	
	public UpperField( String s) {
		this( s, 7, false);
	}

	public UpperField( String s, boolean allowLowerCase) {
		this( s, 7, allowLowerCase);
	}

	public UpperField( String s, int i) {
		this( s, i, false);
	}
	
	public UpperField( String s, int i, boolean allowLowerCase) {
		super( i);
		
		if (!allowLowerCase) {
			setDocument( new PlainDocument() {
				@Override public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
					super.insertString(offs, str.toUpperCase(), a);
				}
			});
		}
		
		setText( s);
	}
	
	public void setText( double v) {
		if (v == Double.MAX_VALUE || v == 0) {
			m_dval = v;
			setText( null);
		}
		else {
			super.setText( "" + v);
		}
	}

	public void setText( int v) {
		if (v == Integer.MAX_VALUE || v == 0) {
			m_ival = v;
			setText( null);
		}
		else {
			super.setText( "" + v);
		}
	}
	
	public double getDouble() {
		try {
			String str = super.getText();
			return str == null || str.length() == 0
				 ? m_dval : Double.parseDouble( super.getText().trim() );
		}
		catch( Exception e) {
			return 0;
		}
	}

	public int getInt() {
		try {
			String str = super.getText();
			return str == null || str.length() == 0
				 ? m_ival : Integer.parseInt( super.getText().trim() );
		}
		catch( Exception e) {
			return 0;
		}
	}
}
