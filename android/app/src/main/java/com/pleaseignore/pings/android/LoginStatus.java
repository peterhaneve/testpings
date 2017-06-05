package com.pleaseignore.pings.android;

import com.google.api.client.util.Key;

/**
 * Used for parsing the JSON response from the server indicating login status.
 */
public final class LoginStatus {
	@Key
	public String challenge;
	@Key
	public boolean valid;
}
