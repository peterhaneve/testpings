package com.pleaseignore.pings.server;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.*;

/**
 * Represents the device information returned from the Google Instance ID API to determine to
 * which topics a device is already subscribed.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class DeviceInfo {
	/**
	 * Should always be the package name of the ping application.
	 */
	public String application;
	/**
	 * The version this client is running.
	 */
	public String applicationVersion;
	/**
	 * Is device rooted? Can be "ROOTED", "NOT_ROOTED", or "UNKNOWN".
	 */
	public String attestStatus;
	/**
	 * Last seen connection date.
	 */
	public String connectDate;
	/**
	 * Last seen connection type, can be "WIFI", "MOBILE", or "OTHER".
	 */
	public String connectionType;
	/**
	 * Platform, can be "ANDROID", "IOS", or "CHROME".
	 */
	public String platform;
	/**
	 * Relations returned by details=true to list topic subscriptions.
	 */
	public DeviceInfoRelations rel;

	/**
	 * Wrapper class for the "rel" information in the device info packet.
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public final class DeviceInfoRelations {
		/**
		 * Maps topic names to a composite object indicating when they were subscribed.
		 */
		public Map<String, Object> topics;

		public DeviceInfoRelations() {
			topics = new HashMap<>(32);
		}
	}
}
