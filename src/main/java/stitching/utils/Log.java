/*
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2007 - 2017 Fiji
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
package stitching.utils;

import ij.IJ;

import java.util.Date;

/**
 * Utility class to deal with logging.
 *
 * @author Curtis Rueden
 */
public final class Log {

	/** Issues a message to the ImageJ log window, but only in debug mode. */
	public static void debug(final String message) {
		if (!IJ.debugMode) return;
		IJ.log(message);
	}

	/** Issues a warning message to the ImageJ log window. */
	public static void warn(final String message) {
		IJ.log("WARNING: " + message);
	}

	/** Issues an informational message to the ImageJ log window. */
	public static void info(final String message) {
		IJ.log(message);
	}

	/** Issues an informational message with timestamp to the ImageJ log window. */
	public static void timestamp(final String message) {
		info("(" + new Date(System.currentTimeMillis()) + "): " + message);
	}

	/**
	 * Issues an error message to the ImageJ log window.
	 * 
	 * @see IJ#log(String)
	 */
	public static void error(final String message) {
		error(message, null);
	}

	/**
	 * Issues an exception stack trace to an ImageJ exception window.
	 * 
	 * @see IJ#handleException(Throwable)
	 */
	public static void error(final Throwable t) {
		error(null, t);
	}

	/**
	 * Issues an error message to the ImageJ log window, plus an exception stack
	 * trace to an ImageJ exception window.
	 * 
	 * @see IJ#log(String)
	 * @see IJ#handleException(Throwable)
	 */
	public static void error(final String message, final Throwable t) {
		if (t != null) t.printStackTrace();
		final String logMessage = message != null ? message : message(t);
		if (logMessage != null) IJ.log("ERROR: " + logMessage);
		if (t != null) IJ.handleException(t);
	}

	private static String message(final Throwable t) {
		return t == null ? null : t.getMessage();
	}

}
