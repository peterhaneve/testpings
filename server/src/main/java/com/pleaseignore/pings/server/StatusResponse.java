package com.pleaseignore.pings.server;

/**
 * A wrapper class for simple JSON responses sent back to server commands.
 */
public final class StatusResponse {
	/**
	 * The response back to the client - human readable.
	 */
	public String response;

	public StatusResponse() {
		response = "";
	}
	public StatusResponse(final String response) {
		if (response == null)
			throw new IllegalArgumentException("response");
		this.response = response;
	}
	public String toString() {
		return response;
	}
}
