package com.pleaseignore.pings.server;

/**
 * A wrapper class for simple JSON responses sent back to login commands.
 */
public final class LoginResponse {
	/**
	 * The login challenge code if successful.
	 */
	public String challenge;
	/**
	 * Whether the login was valid.
	 */
	public boolean valid;

	public LoginResponse() {
		challenge = "";
		valid = false;
	}
	public LoginResponse(final String challenge) {
		if (challenge == null)
			throw new IllegalArgumentException("challenge");
		this.challenge = challenge;
		valid = challenge.length() > 0;
	}
	public String toString() {
		return challenge;
	}
}
