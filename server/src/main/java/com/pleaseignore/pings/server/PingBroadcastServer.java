package com.pleaseignore.pings.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import de.bytefish.fcmjava.client.FcmClient;
import de.bytefish.fcmjava.client.settings.PropertiesBasedSettings;
import de.bytefish.fcmjava.model.enums.PriorityEnum;
import de.bytefish.fcmjava.model.options.FcmMessageOptions;
import de.bytefish.fcmjava.model.topics.Topic;
import de.bytefish.fcmjava.requests.topic.TopicUnicastMessage;
import de.bytefish.fcmjava.responses.TopicMessageResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.*;

/**
 * Broadcasts pings to connected FCM clients. This is a stub server intended to prove the
 * concept of TEST Pings, and therefore does not consult TEST External Auth or intend to
 * integrate effectively with current TEST ping infrastructure. While possible to adapt, it is
 * recommended to rewrite as a module (in Java or otherwise) to the current ping/auth subsystem.
 */
public final class PingBroadcastServer {
	/**
	 * Key used in ping data to store the ping group name.
	 */
	private static final String PING_KEY_GROUP = "group";
	/**
	 * Key used in ping data to store the full ping text.
	 */
	private static final String PING_KEY_MESSAGE = "message";
	/**
	 * Port used to run the server.
	 */
	private static final int SERVER_PORT = 8080;
	/**
	 * Characters to be used in random topic names.
	 */
	private static final String TOPIC_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
	/**
	 * Number of characters in a random topic name.
	 */
	private static final int TOPIC_LEN = 24;

	/**
	 * Creates a new topic ID. Currently 24 alphanumeric characters from CH_CHARS.
	 *
	 * @return the topic ID
	 */
	public static String createTopicID() {
		final Random seed = new Random();
		final int maxLen = TOPIC_CHARS.length();
		final StringBuilder ret = new StringBuilder(TOPIC_LEN);
		for (int i = 0; i < TOPIC_LEN; i++)
			ret.append(TOPIC_CHARS.charAt(seed.nextInt(maxLen)));
		return ret.toString();
	}
	public static void main(String[] args) {
		try {
			final PingBroadcastServer server = new PingBroadcastServer();
			server.start();
			// Wait for ENTER press, then shut down
			final BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
			try {
				br.readLine();
			} catch (IOException ignore) { }
			server.stop();
		} catch (PingServerException e) {
			e.printStackTrace();
		}
	}

	/**
	 * The Firebase client used for sending messages.
	 *
	 * To set api key, create a file in "user home"/.fcmjava/fcmjava.properties and set:
	 * fcm.api.url = https://fcm.googleapis.com/fcm/send
	 * fcm.api.key = "api key"
	 */
	private final FcmClient client;
	/**
	 * Map groups to randomized topic IDs.
	 */
	private final Map<String, String> groupMap;
	/**
	 * Manages the topic subscriptions for all users.
	 */
	private final InstanceIDManager manager;
	/**
	 * HTTP server instance for ping command listening.
	 */
	private HttpServer server;
	/**
	 * Stores active sessions. In a real server this needs to be a file or database object.
	 */
	private final Map<String, UserSession> users;

	private PingBroadcastServer() {
		groupMap = new HashMap<>(32);
		final PropertiesBasedSettings settings = PropertiesBasedSettings.createFromDefault();
		client = new FcmClient(settings);
		manager = new InstanceIDManager(settings);
		server = null;
		users = new HashMap<>(128);
	}
	/**
	 * Filters the sessions, reporting only those subscribed to the specified group name
	 * (English name, not FCM ID)
	 *
	 * @param group the group to check
	 * @return all sessions subscribed to this group (no expiry check)
	 */
	private Collection<UserSession> filterSessions(final String group) {
		final Collection<UserSession> ret;
		if (group == null)
			ret = users.values();
		else {
			// Look for users
			ret = new LinkedList<>();
			for (final UserSession session : users.values()) {
				// Search for group in the list (case sensitive)
				boolean found = false;
				for (final String subscribed : session.getGroups())
					if (group.equals(subscribed)) {
						found = true;
						break;
					}
				if (found)
					ret.add(session);
			}
		}
		return ret;
	}
	/**
	 * Rotates all groups to new topic names, mass unsubscribes all known clients from the
	 * old names, and resubscribes all non-expired users to the new names. Implicitly destroys
	 * users which have expired.
	 */
	private void rotateGroups() {
		synchronized (groupMap) {
			final Map<String, String> newGroupMap = new HashMap<>(groupMap.size());
			// Iterate all groups
			for (final Map.Entry<String, String> group : groupMap.entrySet()) {
				final String groupCode = group.getValue();
				// Unsub users from this group even if expired
				if (groupCode.length() > 0)
					try {
						manager.removeClientsFromTopic(users.values(), groupCode);
					} catch (IOException e) {
						// Should be non-fatal, more robust solutions would retry
						e.printStackTrace();
					}
				// Generate new topic IDs for the groups
				newGroupMap.put(group.getKey(), createTopicID());
			}
			// Load new topic IDs
			groupMap.clear();
			for (final Map.Entry<String, String> group : newGroupMap.entrySet())
				groupMap.put(group.getKey(), group.getValue());
			// Clear out users whose refresh has expired
			final Collection<String> userList = new LinkedList<>(users.keySet());
			for (final String user : userList)
				if (users.get(user).isExpired())
					users.remove(user);
			// Selective subscribe
			for (final Map.Entry<String, String> group : groupMap.entrySet())
				try {
					manager.addClientsToTopic(filterSessions(group.getKey()), group.getValue());
				} catch (IOException e) {
					// Should raise a warning, more robust solutions would retry
					e.printStackTrace();
				}
		}
	}
	/**
	 * Sends a ping to the specified group.
	 *
	 * @param text the ping text
	 * @param group the group to ping
	 * @throws PingFailedException if the request for ping fails
	 */
	private void sendPing(final String text, final String group) throws PingFailedException {
		synchronized (groupMap) {
			// Find matching topic
			if (groupMap.containsKey(group)) {
				final String groupCode = groupMap.get(group);
				// Set up message options - 1 day expiry, high priority (allow device wake)
				final FcmMessageOptions options = FcmMessageOptions.builder().setTimeToLive(
					Duration.ofDays(1L)).setPriorityEnum(PriorityEnum.High).build();
				// Create message payload
				final Map<String, Object> payload = new HashMap<String, Object>(8);
				payload.put(PING_KEY_GROUP, group);
				payload.put(PING_KEY_MESSAGE, text);
				// Send to the randomized group ID
				final TopicUnicastMessage message = new TopicUnicastMessage(options,
					new Topic(groupCode), payload);
				final TopicMessageResponse response = client.send(message);
				if (response.getErrorCode() != null)
					throw new PingFailedException("Response error: " + response.getErrorCode());
			} else
				throw new PingFailedException("Invalid ping group: " + group);
		}
	}
	/**
	 * Starts a ping broadcast server.
	 *
	 * @throws PingServerException if an error occurs during startup
	 */
	private void start() throws PingServerException {
		// Create some dummy groups
		synchronized (groupMap) {
			groupMap.put("all", "");
			groupMap.put("caps", "");
			groupMap.put("supers", "");
		}
		// TODO On start up (or every X rotations):
		//  The server needs to force drop all group memberships for each active account, to
		//  avoid errors when the device ID hits the maximum number of topics
		rotateGroups();
		try {
			// Could use HttpsServer, but this is a demo anyways and it would cause certificate
			// problems
			server = HttpServer.create(new InetSocketAddress(SERVER_PORT), 32);
			server.createContext("/forceRefresh", new ForceRefreshHandler());
			server.createContext("/login", new LoginHandler());
			server.createContext("/ping", new PingHandler());
			// TODO "refresh"
			server.start();
		} catch (IOException e) {
			throw new PingServerException("When starting ping server", e);
		}
	}
	/**
	 * Stops the server.
	 *
	 * @throws PingServerException if an error occurs during shutdown
	 */
	private void stop() throws PingServerException {
		try {
			if (server != null)
				server.stop(2);
			client.close();
		} catch (Exception e) {
			throw new PingServerException("When shutting down", e);
		}
	}
	/**
	 * Updates a user's subscriptions when they log on, unsubscribing from old topics and adding
	 * the latest and greatest IDs.
	 *
	 * @param session the session to update
	 */
	private void updateUser(final UserSession session) {
		synchronized (groupMap) {
			// https://iid.googleapis.com/iid/info/<device ID>
		}
	}

	/**
	 * Handles force refresh commands by cycling the topic IDs.
	 */
	private final class ForceRefreshHandler implements HttpHandler {
		public void handle(HttpExchange exchange) throws IOException {
			rotateGroups();
			HttpUtilities.sendResponse(exchange, "done\r\n");
		}
	}

	/**
	 * Handles logins (stub class) and adds them to the ping list.
	 */
	private final class LoginHandler implements HttpHandler {
		/**
		 * Dummy password for testing.
		 */
		private static final String PASSWORD = "password";

		public void handle(HttpExchange exchange) throws IOException {
			// Obtain POST data if the method is POST
			if ("POST".equals(exchange.getRequestMethod())) {
				String response = "invalid";
				final Map<String, String> postData = HttpUtilities.parseFormData(
					HttpUtilities.getRequestBody(exchange));
				// If this was a valid request
				if (postData.containsKey("username") && postData.containsKey("password") &&
						postData.containsKey("deviceID")) {
					final String username = postData.get("username"), password =
						postData.get("password"), deviceID = postData.get("deviceID");
					if (PASSWORD.equals(password) && username != null &&
							groupMap.containsKey(username) && deviceID != null) {
						// Create and store session information, destroying old session if it
						// exists for that username
						final Collection<String> groups = new LinkedList<>();
						// Make groups the username
						groups.add(username);
						groups.add("all");
						final UserSession session = new UserSession(deviceID, groups);
						response = session.getChallengeToken();
						users.put(username, session);
						// Should go on work queue (separate thread)
						updateUser(session);
					}
				}
				HttpUtilities.sendResponse(exchange, response + "\r\n");
			} else
				// Bad request!
				exchange.sendResponseHeaders(400, 0);
		}
	}

	/**
	 * Handles ping commands and actually sends out pings!
	 */
	private final class PingHandler implements HttpHandler {
		public void handle(HttpExchange exchange) throws IOException {
			if ("GET".equals(exchange.getRequestMethod())) {
				String response = "invalid", group = null, pingText;
				final Map<String, String> getData = HttpUtilities.parseFormData(
					exchange.getRequestURI().getQuery());
				if (getData.containsKey("body")) {
					pingText = getData.get("body");
					if (pingText == null)
						pingText = "";
					// Default group to "all"
					if (getData.containsKey("group"))
						group = getData.get("group");
					if (group == null)
						group = "all";
					if (groupMap.containsKey(group))
						// Valid group, ping it out
						try {
							sendPing(pingText, group);
							response = "sent";
						} catch (PingFailedException e) {
							// Ping failed, tell the user
							response = "failed: " + e.getMessage();
						}
					else
						// Group name not found
						response = "badGroup";
				}
				HttpUtilities.sendResponse(exchange, response + "\r\n");
			} else
				// Bad request!
				exchange.sendResponseHeaders(400, 0);
		}
	}
}
