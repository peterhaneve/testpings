package com.pleaseignore.pings.server;

import com.fasterxml.jackson.core.JsonFactory;
import de.bytefish.fcmjava.client.settings.PropertiesBasedSettings;

import java.io.IOException;
import java.util.*;

/**
 * A class to handle the Instance ID API for mass adding and removing clients to topics.
 */
public final class InstanceIDManager {
	/**
	 * Creates a JSON body that will subscribe/unsubscribe the specified clients from the topic.
	 *
	 * @param clients the clients to modify
	 * @param target the target topic ID
	 * @return the appopriate JSON body content listing these users
	 */
	private static String createRequestBody(final Collection<UserSession> clients,
											final String target) {
		final StringBuilder ret = new StringBuilder(2048);
		// Header: topic, assume target has only legal characters
		ret.append("{\r\n\t\"to\": \"/topics/");
		ret.append(target);
		ret.append("\",\r\n\t\"registration_tokens\": [");
		for (final UserSession client : clients) {
			ret.append('"');
			ret.append(client.getDeviceID());
			// JSON allows hanging last commas
			ret.append("\", ");
		}
		ret.append("]\r\n}\r\n");
		return ret.toString();
	}

	/**
	 * The API key for this application.
	 */
	private final String apiKey;

	/**
	 * Creates a new instance ID manager using the provided FCM API key.
	 *
	 * @param settings the settings containing the FCM API key
	 */
	public InstanceIDManager(final PropertiesBasedSettings settings) {
		apiKey = settings.getApiKey();
	}
	/**
	 * Adds all of these clients to the specified topic ID.
	 *
	 * @param clients the clients to add
	 * @param topicID the FCM topic ID to subscribe
	 * @throws IOException if an I/O error occurs during the change
	 */
	public void addClientsToTopic(final Collection<UserSession> clients, final String topicID)
			throws IOException {
		HttpUtilities.makeRequest("https://iid.googleapis.com/iid/v1:batchAdd", apiKey,
			createRequestBody(clients, topicID));
	}
	/**
	 * Removes all of these clients from the specified topic ID.
	 *
	 * @param clients the clients to remove
	 * @param topicID the FCM topic ID to unsubscribe
	 * @throws IOException if an I/O error occurs during the change
	 */
	public void removeClientsFromTopic(final Collection<UserSession> clients,
									   final String topicID) throws IOException {
		HttpUtilities.makeRequest("https://iid.googleapis.com/iid/v1:batchRemove", apiKey,
			createRequestBody(clients, topicID));
	}
}
