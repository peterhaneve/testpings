package com.pleaseignore.pings.server;

/**
 * A class representing an exception thrown when a ping fails.
 */
public class PingFailedException extends Exception {
	private static final long serialVersionUID = 9055180461467489537L;

	public PingFailedException() {
	}
	public PingFailedException(String message) {
		super(message);
	}
	public PingFailedException(String message, Throwable cause) {
		super(message, cause);
	}
	public PingFailedException(Throwable cause) {
		super(cause);
	}
}
