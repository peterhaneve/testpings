package com.pleaseignore.pings.server;

/**
 * A JSON wrapper class to add or remove clients to a topic.
 */
public final class TopicModifyRequest {
	/**
	 * Registration tokens to manipulate.
	 */
	public String[] registration_tokens;
	/**
	 * The request target (if using a topic name, must be prefixed with /topic/)
	 */
	public String to;

	public TopicModifyRequest() {
		registration_tokens = new String[0];
		to = "";
	}
	/**
	 * Creates a new topic modification request.
	 *
	 * @param to the target (if using a topic name, must be prefixed with /topic/)
	 * @param tokens the tokens to target (warning: no copy is made)
	 */
	public TopicModifyRequest(final String to, final String[] tokens) {
		if (to == null)
			throw new IllegalArgumentException("to");
		registration_tokens = tokens;
		this.to = to;
	}
}
