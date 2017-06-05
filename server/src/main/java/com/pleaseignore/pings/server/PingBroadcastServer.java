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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Broadcasts pings to connected FCM clients. This is a stub server intended to prove the
 * concept of TEST Pings, and therefore does not consult TEST External Auth or intend to
 * integrate effectively with current TEST ping infrastructure. While possible to adapt, it is
 * recommended to rewrite as a module (in Java or otherwise) to the current ping/auth subsystem.
 */
public final class PingBroadcastServer implements Runnable {
	/**
	 * Key used in ping data to store the ping group name.
	 */
	private static final String PING_KEY_GROUP = "group";
	/**
	 * Key used in ping data to store the full ping text.
	 */
	private static final String PING_KEY_MESSAGE = "message";
	/**
	 * The maximum number of retries allowed for a background request.
	 */
	private static final int RETRY_COUNT = 3;
	/**
	 * The time to wait between retries in milliseconds.
	 */
	private static final long RETRY_INTERVAL = 2000L;
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
	 * Manages the topic subscriptions for all users.
	 */
	private final InstanceIDManager manager;
	/**
	 * HTTP server instance for ping command listening.
	 */
	private HttpServer server;
	/**
	 * Thread pool for handling routine tasks.
	 */
	private final ScheduledExecutorService threadPool;
	/**
	 * Maps groups to randomized topic IDs.
	 */
	private final Map<String, String> topicMap;
	/**
	 * Stores active sessions. In a real server this needs to be a file or database object.
	 */
	private final Map<String, UserSession> users;

	private PingBroadcastServer() {
		final PropertiesBasedSettings settings = PropertiesBasedSettings.createFromDefault();
		client = new FcmClient(settings);
		manager = new InstanceIDManager(settings);
		server = null;
		threadPool = Executors.newScheduledThreadPool(2);
		topicMap = new HashMap<>(32);
		users = new HashMap<>(128);
	}
	/**
	 * Filters the sessions, reporting only those subscribed to the specified group name
	 * (English name, not FCM ID). Assumes that the topicMap is locked to prevent sessions
	 * from being modified during the search.
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
		synchronized (topicMap) {
			final Map<String, String> newGroupMap = new HashMap<>(topicMap.size());
			for (final Map.Entry<String, String> entry : topicMap.entrySet()) {
				final String topic = entry.getValue();
				// Unsubscribe users from this topic even if expired
				if (topic.length() > 0)
					threadPool.submit(new RemoveClientsFromTopicTask(users.values(), topic));
				// Generate new topic IDs for the groups
				newGroupMap.put(entry.getKey(), createTopicID());
			}
			// Load new topic IDs
			topicMap.clear();
			for (final Map.Entry<String, String> entry : newGroupMap.entrySet())
				topicMap.put(entry.getKey(), entry.getValue());
			// Clear out users whose refresh has expired
			final Collection<String> userList = new LinkedList<>(users.keySet());
			for (final String user : userList)
				if (users.get(user).isExpired())
					users.remove(user);
			// Selective subscribe sessions to each topic in bulk
			for (final Map.Entry<String, String> entry : topicMap.entrySet()) {
				final Collection<UserSession> sessions = filterSessions(entry.getKey());
				threadPool.submit(new AddClientsToTopicTask(sessions, entry.getValue()));
			}
		}
	}
	public void run() {
		rotateGroups();
	}
	/**
	 * Sends a ping to the specified group.
	 *
	 * @param text the ping text
	 * @param group the group to ping
	 * @throws PingFailedException if the request for ping fails
	 */
	private void sendPing(final String text, final String group) throws PingFailedException {
		synchronized (topicMap) {
			// Find matching topic
			if (topicMap.containsKey(group)) {
				final String groupCode = topicMap.get(group);
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
		synchronized (topicMap) {
			topicMap.put("all", "");
			topicMap.put("caps", "");
			topicMap.put("supers", "");
		}
		// Add rotation task - TODO move to downtime every day
		threadPool.scheduleAtFixedRate(this, 0L, 1L, TimeUnit.DAYS);
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
			// Stop the web server
			if (server != null)
				server.stop(2);
			// Stop any outstanding tasks
			threadPool.shutdown();
			threadPool.awaitTermination(2L, TimeUnit.SECONDS);
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
		synchronized (topicMap) {
			// https://iid.googleapis.com/iid/info/<device ID>
			final Collection<UserSession> sessions = Collections.singletonList(session);
			for (final String topic : session.getGroups())
				threadPool.submit(new AddClientsToTopicTask(sessions, topic));
		}
	}

	/**
	 * Handles force refresh commands by cycling the topic IDs. This command is meant for
	 * debugging only.
	 */
	private final class ForceRefreshHandler implements HttpHandler {
		public void handle(HttpExchange exchange) throws IOException {
			rotateGroups();
			HttpUtilities.sendResponse(exchange, "{\n\tresponse: \"done\"\n}\n");
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
				String token = "";
				final Map<String, String> postData = HttpUtilities.parseFormData(
					HttpUtilities.getRequestBody(exchange));
				// If this was a valid request
				if (postData.containsKey("username") && postData.containsKey("password") &&
						postData.containsKey("deviceID")) {
					final String username = postData.get("username"), password =
						postData.get("password"), deviceID = postData.get("deviceID");
					// Create and store session information, destroying old session if it
					// exists for that username
					if (PASSWORD.equals(password) && username != null &&
							topicMap.containsKey(username) && deviceID != null) {
						final Collection<String> groups = new LinkedList<>();
						// Make groups the username
						groups.add(username);
						groups.add("all");
						final UserSession session = new UserSession(deviceID, groups);
						token = session.getChallengeToken();
						users.put(username, session);
						// Should go on work queue (separate thread)
						updateUser(session);
					}
				}
				// Create JSON response
				final String response = "{\n\tvalid: " + (token.length() > 0) +
					",\n\tchallenge: \"" + token + "\"\n}\n";
				HttpUtilities.sendResponse(exchange, response);
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
					if (topicMap.containsKey(group))
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
				HttpUtilities.sendResponse(exchange, "{\n\tresponse: \"" + response + "\"\n}\n");
			} else
				// Bad request!
				exchange.sendResponseHeaders(400, 0);
		}
	}

	/**
	 * A task which adds or removes clients to/from topics. Intended to be run on the thread
	 * pool, and schedules up to the specified retry limit if an error occurs.
	 */
	private static abstract class ClientChangeTask extends RetriableTask {
		/**
		 * The device IDs to be added.
		 */
		protected final Collection<UserSession> sessions;
		/**
		 * The topic to which the sessions will be (un)subscribed.
		 */
		protected final String topic;

		/**
		 * Creates a new client change task.
		 *
		 * @param sessions the clients to change
		 * @param topic the target topic
		 */
		protected ClientChangeTask(final Collection<UserSession> sessions, final String topic) {
			super(0);
			if (sessions == null)
				throw new IllegalArgumentException("sessions");
			if (topic == null)
				throw new IllegalArgumentException("topic");
			this.sessions = sessions;
			this.topic = topic;
		}
		protected ClientChangeTask(final ClientChangeTask original) {
			super(original.getRetries() + 1);
			sessions = original.sessions;
			topic = original.topic;
		}
	}

	/**
	 * A task which adds the specified clients to a topic.
	 */
	private final class AddClientsToTopicTask extends ClientChangeTask {
		public AddClientsToTopicTask(final Collection<UserSession> sessions,
									 final String topic) {
			super(sessions, topic);
		}
		private AddClientsToTopicTask(final ClientChangeTask original) {
			super(original);
		}
		public void run() {
			try {
				// Perform the request
				manager.addClientsToTopic(sessions, topic);
			} catch (IOException e) {
				e.printStackTrace();
				// Retry if possible after the interval
				if (getRetries() < RETRY_COUNT)
					threadPool.schedule(new AddClientsToTopicTask(this), RETRY_INTERVAL,
						TimeUnit.MILLISECONDS);
			}
		}
	}

	/**
	 * A task which removes the specified clients from a topic.
	 */
	private final class RemoveClientsFromTopicTask extends ClientChangeTask {
		public RemoveClientsFromTopicTask(final Collection<UserSession> sessions,
										  final String topic) {
			super(sessions, topic);
		}
		private RemoveClientsFromTopicTask(final ClientChangeTask original) {
			super(original);
		}
		public void run() {
			try {
				// Perform the request
				manager.removeClientsFromTopic(sessions, topic);
			} catch (IOException e) {
				e.printStackTrace();
				// Retry if possible after the interval
				if (getRetries() < RETRY_COUNT)
					threadPool.schedule(new RemoveClientsFromTopicTask(this), RETRY_INTERVAL,
						TimeUnit.MILLISECONDS);
			}
		}
	}
}
