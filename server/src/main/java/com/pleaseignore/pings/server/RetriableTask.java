package com.pleaseignore.pings.server;

/**
 * A Runnable framework to be run on thread pools which can count the number of retries so far.
 */
public abstract class RetriableTask implements Runnable {
	/**
	 * The number of retries that have occurred on this task.
	 */
	protected final int retries;

	protected RetriableTask(final int retries) {
		if (retries < 0)
			throw new IllegalArgumentException("retries");
		this.retries = retries;
	}
	/**
	 * Reports the number of retries that have already occurred on this task.
	 *
	 * @return the number of retries so far
	 */
	public int getRetries() {
		return retries;
	}
}
