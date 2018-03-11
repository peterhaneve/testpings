package com.pleaseignore.pings.server;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Broadcasts pings to connected FCM clients. This is a stub server intended to prove the
 * concept of TEST Pings, and therefore does not consult TEST External Auth or intend to
 * integrate effectively with current TEST ping infrastructure. While possible to adapt, it is
 * recommended to rewrite as a module (in Java or otherwise) to the current ping/auth subsystem.
 */
public final class PingBroadcastServer implements Runnable {
	/**
	 * Logs any unusual errors which occur in this class (most are passed upstream)
	 */
	private static final Logger LOGGER = Logger.getLogger(PingBroadcastServer.class.getName());
	/**
	 * Used to convert objects to and from JSON.
	 */
	private static final ObjectMapper MAPPER = new ObjectMapper();
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
			// Use interrupt() to stop
			do {
				final long interval = 60000L * Math.round(60.0 * (1.0 + Math.random() * 4.0));
				try {
					Thread.sleep(interval);
				} catch (InterruptedException e) {
					// Shut down
					break;
				}
				// Send a dummy ping
				server.sendPing("Ping was sent at " + new Date().toString(), "all");
			} while (true);
			server.stop();
		} catch (PingServerException e) {
			// Exception when starting/stopping server
			LOGGER.log(Level.SEVERE, "Error when starting ping server", e);
		} catch (Exception e) {
			// Unknown exception!
			LOGGER.log(Level.SEVERE, "Unhandled exception in ping server!", e);
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
	 * Retrieves a list of topic codes to which the specified session should be subscribed.
	 *
	 * @param session the session to query
	 * @return the FCM topic IDs to which this session should be subscribed
	 */
	private Set<String> getTopicCodes(final UserSession session) {
		final Collection<String> groups = session.getGroups();
		// Add to set for fast lookup by name
		final Set<String> topics = new HashSet<String>(groups.size() * 2);
		synchronized (topicMap) {
			for (final String group : groups)
				if (topicMap.containsKey(group)) {
					final String topicCode = topicMap.get(group);
					if (topicCode != null)
						topics.add(topicCode);
				}
		}
		return topics;
	}
	/**
	 * Rotates all groups to new topic names, mass unsubscribes all known clients from the
	 * old names, and resubscribes all non-expired users to the new names. Implicitly destroys
	 * users which have expired.
	 */
	private void rotateGroups() {
		synchronized (topicMap) {
			LOGGER.log(Level.FINE, "Refreshing groups");
			// Create a temporary list of the new topic IDs
			final Map<String, String> newGroupMap = new HashMap<>(topicMap.size());
			final Collection<UserSession> allUsers = users.values();
			for (final Map.Entry<String, String> entry : topicMap.entrySet()) {
				final String topic = entry.getValue(), group = entry.getKey();
				// Unsubscribe users from this topic even if expired
				if (topic.length() > 0 && allUsers.size() > 0)
					threadPool.submit(new RemoveClientsFromTopicTask(allUsers, topic));
				// Generate new topic IDs for the groups
				final String topicID = createTopicID();
				newGroupMap.put(group, topicID);
				LOGGER.log(Level.FINE, "Group \"" + group + "\" => " + topicID);
			}
			// Load new topic IDs
			topicMap.clear();
			for (final Map.Entry<String, String> entry : newGroupMap.entrySet())
				topicMap.put(entry.getKey(), entry.getValue());
			// Clear out users whose refresh has expired
			final Collection<String> userList = new LinkedList<>(users.keySet());
			for (final String user : userList)
				if (users.get(user).isExpired()) {
					users.remove(user);
					LOGGER.log(Level.FINE, "Expired user \"" + user + "\"");
				}
			// Selective subscribe sessions to each topic in bulk
			for (final Map.Entry<String, String> entry : topicMap.entrySet()) {
				final Collection<UserSession> sessions = filterSessions(entry.getKey());
				if (sessions.size() > 0)
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
				final String topicCode = topicMap.get(group);
				// Set up message options - high priority (allow device wake)
				final FcmMessageOptions options = FcmMessageOptions.builder().
					setPriorityEnum(PriorityEnum.High).build();
				// Create message payload
				final Map<String, Object> payload = new HashMap<String, Object>(8);
				payload.put(PING_KEY_GROUP, group);
				payload.put(PING_KEY_MESSAGE, text);
				// Send to the randomized group ID
				final TopicUnicastMessage message = new TopicUnicastMessage(options,
					new Topic(topicCode), payload);
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
			server.createContext("/refresh", new ChallengeHandler());
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
	 * Handles challenge responses from clients and refreshes their login timeout if they do
	 * so successfully. If the challenge is wrong, do not expire the user immediately, or else
	 * there is an easy avenue for a DoS attack.
	 */
	private final class ChallengeHandler implements HttpHandler {
		public void handle(HttpExchange exchange) throws IOException {
			if ("POST".equals(exchange.getRequestMethod())) {
				// Obtain POST data if the method is POST
				String token = "", username = null, challenge = null;
				final List<NameValuePair> postData = URLEncodedUtils.parse(HttpUtilities.
					getRequestBody(exchange), Charset.forName(HttpUtilities.ENCODING));
				for (final NameValuePair param : postData) {
					final String key = param.getName(), value = param.getValue();
					// Extract parameters of the login request
					if (key.equals("username"))
						username = value;
					else if (key.equals("challenge"))
						challenge = value;
				}
				if (username != null && challenge != null)
					synchronized (topicMap) {
						// Verify the challenge; if good, give them another lease on life
						final UserSession session = users.get(username);
						final String correct = session.getChallengeToken();
						if (!session.isExpired() && correct.equals(challenge)) {
							session.updateLogin();
							LOGGER.log(Level.FINE, "Renewed user \"" + username + "\"");
							token = challenge;
						}
					}
				HttpUtilities.sendResponse(exchange, MAPPER.writeValueAsString(new
					LoginResponse(token)));
			} else
				// Bad request!
				exchange.sendResponseHeaders(400, 0);
		}
	}

	/**
	 * Handles force refresh commands by cycling the topic IDs. This command is meant for
	 * debugging only.
	 */
	private final class ForceRefreshHandler implements HttpHandler {
		public void handle(HttpExchange exchange) throws IOException {
			rotateGroups();
			HttpUtilities.sendResponse(exchange, MAPPER.writeValueAsString(new StatusResponse(
				"done")));
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
			if ("POST".equals(exchange.getRequestMethod())) {
				// Obtain POST data if the method is POST
				String token = "", username = null, password = null, deviceID = null;
				final List<NameValuePair> postData = URLEncodedUtils.parse(HttpUtilities.
					getRequestBody(exchange), Charset.forName(HttpUtilities.ENCODING));
				for (final NameValuePair param : postData) {
					final String key = param.getName(), value = param.getValue();
					// Extract parameters of the login request
					switch (key) {
					case "username":
						username = value;
						break;
					case "password":
						password = value;
						break;
					case "deviceID":
						deviceID = value;
						break;
					default:
						break;
					}
				}
				// If username and password are valid
				if (PASSWORD.equals(password) && username != null && topicMap.containsKey(
						username) && deviceID != null && !username.equals("all")) {
					final Collection<String> groups = new LinkedList<>();
					// TODO Subscribe to group matching username
					groups.add(username);
					groups.add("all");
					final UserSession session = new UserSession(deviceID, groups);
					token = session.getChallengeToken();
					synchronized (topicMap) {
						users.put(username, session);
					}
					LOGGER.log(Level.FINE, "User \"" + username + "\" logged in");
					// Get the user integrated on a separate task
					threadPool.submit(new UpdateUserTask(session));
				}
				HttpUtilities.sendResponse(exchange, MAPPER.writeValueAsString(new
					LoginResponse(token)));
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
				String response = "invalid", group = null, pingText = null;
				// The URL should be 7-bit safe anyways
				final List<NameValuePair> getData = URLEncodedUtils.parse(exchange.
					getRequestURI().getQuery(), Charset.forName(HttpUtilities.ENCODING));
				for (final NameValuePair param : getData) {
					final String key = param.getName(), value = param.getValue();
					// Extract parameters of the ping
					switch (key) {
					case "body":
						pingText = value;
						break;
					case "group":
						group = value;
						break;
					default:
						break;
					}
				}
				if (pingText != null && pingText.length() > 1) {
					final boolean ok;
					// Default group to "all"
					if (group == null || group.length() < 1)
						group = "all";
					synchronized (topicMap) {
						ok = topicMap.containsKey(group);
					}
					if (ok)
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
				HttpUtilities.sendResponse(exchange, MAPPER.writeValueAsString(new
					StatusResponse(response)));
			} else
				// Bad request!
				exchange.sendResponseHeaders(400, 0);
		}
	}

	/**
	 * A task which unsubscribes the user from all of its current topics, then resubscribes it
	 * to the appropriate topics that it owns.
	 */
	private final class UpdateUserTask extends RetriableTask {
		/**
		 * The client to update.
		 */
		private final UserSession session;

		public UpdateUserTask(final UserSession session) {
			super(0);
			if (session == null)
				throw new IllegalArgumentException("session");
			this.session = session;
		}
		private UpdateUserTask(final UpdateUserTask original) {
			super(original.getRetries() + 1);
			session = original.session;
		}
		public void run() {
			boolean ok = false;
			final Collection<UserSession> sessions = Collections.singletonList(session);
			final Set<String> shouldHave = getTopicCodes(session);
			// Get list of current subscriptions
			try {
				final Collection<String> topics = manager.listTopics(session.getDeviceID());
				if (topics != null) {
					// If null, then request failed and needs to be retried (could be empty)
					final Collection<String> toRemove = new LinkedList<>(), toAdd =
						new HashSet<>(shouldHave);
					LOGGER.log(Level.FINE, "Current topics: " + topics.toString());
					for (final String topic : topics) {
						if (shouldHave.contains(topic))
							// If topic should exist and is already subscribed, do not resub
							toAdd.remove(topic);
						else
							// Add all groups that it should not have to remove list
							toRemove.add(topic);
					}
					ok = true;
					LOGGER.log(Level.FINE, "Adding to topics: " + toAdd.toString());
					LOGGER.log(Level.FINE, "Removing from topics: " + toRemove.toString());
					// Remove from old groups - if some of these fail, then the task as a whole
					// will be retried, and the ones which did succeed will be reflected in the
					// new session list to avoid redoing work
					for (final String topic : toRemove)
						ok = ok && manager.removeClientsFromTopic(sessions, topic);
					// Add to new ones
					for (final String topic : toAdd)
						ok = ok && manager.addClientsToTopic(sessions, topic);
				}
			} catch (IOException e) {
				LOGGER.log(Level.INFO, "Error updating user \"" + session + "\" (retrying)",
					e);
			}
			final int n = getRetries();
			// Retry if possible after the interval
			if (!ok && n < RETRY_COUNT)
				threadPool.schedule(new UpdateUserTask(this), RETRY_INTERVAL * n,
					TimeUnit.MILLISECONDS);
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
			boolean ok = false;
			try {
				// Perform the request
				ok = manager.addClientsToTopic(sessions, topic);
			} catch (IOException e) {
				LOGGER.log(Level.INFO, "Error when adding users to topic \"" + topic +
					"\" (retrying)", e);
			}
			final int n = getRetries();
			// Retry if possible after the interval
			if (!ok && getRetries() < RETRY_COUNT)
				threadPool.schedule(new AddClientsToTopicTask(this), RETRY_INTERVAL * n,
					TimeUnit.MILLISECONDS);
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
			boolean ok = false;
			try {
				// Perform the request
				ok = manager.removeClientsFromTopic(sessions, topic);
			} catch (IOException e) {
				LOGGER.log(Level.INFO, "Error when removing users from topic \"" + topic +
					"\" (retrying)", e);
			}
			final int n = getRetries();
			// Retry if possible after the interval
			if (!ok && n < RETRY_COUNT)
				threadPool.schedule(new RemoveClientsFromTopicTask(this), RETRY_INTERVAL * n,
					TimeUnit.MILLISECONDS);
		}
	}
}
