package com.pleaseignore.pings.server;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.bytefish.fcmjava.client.settings.PropertiesBasedSettings;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A class to handle the Instance ID API for mass adding and removing clients to topics.
 */
public final class InstanceIDManager {
	/**
	 * The base URL for HTTP requests made through this library.
	 */
	private static final String BASE_URL = "https://iid.googleapis.com/iid/";
	/**
	 * Logs any unusual errors which occur in this class (most are passed upstream).
	 */
	private static final Logger LOGGER = Logger.getLogger(InstanceIDManager.class.getName());
	/**
	 * Used to convert objects to and from JSON.
	 */
	private static final ObjectMapper MAPPER = new ObjectMapper();

	/**
	 * Creates a JSON body that will subscribe/unsubscribe the specified clients from the topic.
	 *
	 * @param clients the clients to modify
	 * @param target the target topic ID
	 * @return the appopriate JSON body content listing these users
	 */
	private static String createRequestBody(final Collection<UserSession> clients,
											final String target) {
		String ret;
		// Convert clients to string array
		final int numClients = clients.size();
		int i = 0;
		final String[] clientIDs = new String[numClients];
		for (final UserSession session : clients)
			clientIDs[i++] = session.getDeviceID();
		// Header: topic
		try {
			ret = MAPPER.writeValueAsString(new TopicModifyRequest("/topics/" + target,
				clientIDs));
		} catch (JsonProcessingException e) {
			LOGGER.log(Level.SEVERE, "JSON error serializing request body", e);
			// Should never happen
			ret = "";
		}
		return ret;
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
	 * @return true if the request succeeded, or false if it failed
	 */
	public boolean addClientsToTopic(final Collection<UserSession> clients,
									 final String topicID) throws IOException {
		return HttpUtilities.makePostRequest(BASE_URL + "v1:batchAdd", apiKey,
			createRequestBody(clients, topicID));
	}
	/**
	 * Lists the topic subscriptions for the device ID.
	 *
	 * @param deviceID the device ID to query
	 * @return a list of topics to which it is subscribed, or null if the request failed
	 */
	public Collection<String> listTopics(final String deviceID) throws IOException {
		final String body = HttpUtilities.makeGetRequest(BASE_URL + "info/" +  deviceID +
			"?details=true", apiKey);
		Collection<String> ret = null;
		// If request had a body, parse it
		if (body != null)
			try {
				final DeviceInfo info = MAPPER.readValue(body, DeviceInfo.class);
				if (info != null) {
					// Chain of null checks is annoying but defensive
					final DeviceInfo.DeviceInfoRelations rel = info.rel;
					if (rel != null) {
						final Map<String, Object> topicList = rel.topics;
						if (topicList != null)
							ret = topicList.keySet();
						else
							ret = Collections.emptyList();
					} else
						// No items
						ret = Collections.emptyList();
				}
			} catch (JsonParseException e) {
				// JSON parse issues
				LOGGER.log(Level.WARNING, "Failed to parse JSON body from Instance ID API \"" +
					body + "\"", e);
			} catch (JsonMappingException e) {
				// Does not match the expected format
				LOGGER.log(Level.WARNING, "JSON body from Instance ID API \"" + body +
					"\" does not match format", e);
			}
		return ret;
	}
	/**
	 * Removes all of these clients from the specified topic ID.
	 *
	 * @param clients the clients to remove
	 * @param topicID the FCM topic ID to unsubscribe
	 * @throws IOException if an I/O error occurs during the change
	 * @return true if the request succeeded, or false if it failed
	 */
	public boolean removeClientsFromTopic(final Collection<UserSession> clients,
										  final String topicID) throws IOException {
		return HttpUtilities.makePostRequest(BASE_URL + "v1:batchRemove", apiKey,
			createRequestBody(clients, topicID));
	}
}
