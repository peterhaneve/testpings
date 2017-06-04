package com.pleaseignore.pings.server;

/**
 * A class representing an exception thrown when an error occurs in ping server setup.
 */
public class PingServerException extends Exception {
	private static final long serialVersionUID = -387383820611802898L;

	public PingServerException() {
	}
	public PingServerException(String message) {
		super(message);
	}
	public PingServerException(String message, Throwable cause) {
		super(message, cause);
	}
	public PingServerException(Throwable cause) {
		super(cause);
	}
}
