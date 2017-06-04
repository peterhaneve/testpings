package com.pleaseignore.pings.server;

import java.util.*;

/**
 * A skeleton class wrapping a user's session. This stores their last login, a cached final list
 * of the groups they are in, and the challenge token to be used.
 */
public final class UserSession {
	/**
	 * The number of milliseconds that an inactive user session remains before being removed.
	 * Should be at least a day.
	 */
	private static final long TTL = 86400000L * 2L;

	/**
	 * Retrieves the current time in UTC.
	 *
	 * @return the current system time in milliseconds (UTC)
	 */
	private static long now() {
		return System.currentTimeMillis();
	}

	/**
	 * Generated at login, used to allow client to throw out username and password.
	 */
	private final String challengeToken;
	/**
	 * The client's Firebase device ID for server side subscriptions.
	 */
	private final String deviceID;
	/**
	 * The groups to which this user has a subscription.
	 */
	private final Collection<String> groups;
	/**
	 * When the user last logged in.
	 */
	private long lastLogin;

	/**
	 * Creates a user session.
	 *
	 * @param groups the groups from which that this user receives pings (no need for "all")
	 */
	public UserSession(final String deviceID, final Collection<String> groups) {
		challengeToken = PingBroadcastServer.createTopicID();
		this.deviceID = deviceID;
		this.groups = groups;
		updateLogin();
	}
	/**
	 * Retrieves the challenge token.
	 *
	 * @return the token used to challenge a client on refresh
	 */
	public String getChallengeToken() {
		return challengeToken;
	}
	/**
	 * Retrieves the user's device ID.
	 *
	 * @return the device ID for server side subscriptions
	 */
	public String getDeviceID() {
		return deviceID;
	}
	/**
	 * Retrieves the user's group list.
	 *
	 * @return the groups from which that this user receives pings
	 */
	public Collection<String> getGroups() {
		return groups;
	}
	/**
	 * Returns true if this session is expired.
	 *
	 * @return if the session has not been refreshed since the time configured in TTL
	 */
	public boolean isExpired() {
		return lastLogin - now() > TTL;
	}
	/**
	 * Updates the last login data on a successful challenge and refresh.
	 */
	public void updateLogin() {
		lastLogin = now();
	}
}
