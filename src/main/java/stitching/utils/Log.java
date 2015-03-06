package stitching.utils;

import ij.IJ;

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
