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


/** Delegate for connection parameters */
public interface IConnectionConfiguration {

	String getDefaultHost();
	String getDefaultPort();
	String getDefaultConnectOptions();

	/** Standard ApiDemo configuration for pre-v100 connection */
	class DefaultConnectionConfiguration implements IConnectionConfiguration {
	    @Override public String getDefaultHost() { return ""; }
	    @Override public String getDefaultPort() { return "7496"; }
	    @Override public String getDefaultConnectOptions() { return "+PACEAPI"; }
	}
}
