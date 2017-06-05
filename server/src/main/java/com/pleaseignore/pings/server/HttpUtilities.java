package com.pleaseignore.pings.server;

import com.sun.net.httpserver.HttpExchange;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import java.io.*;
import java.net.URLDecoder;
import java.util.*;

/**
 * Contains utility functions used for HTTP handlers.
 */
public final class HttpUtilities {
	/**
	 * Buffer size to read at a time from the HTTP request.
	 */
	private static final int BUFFER_LEN = 1024;
	/**
	 * The encoding to use for all requests.
	 */
	private static final String ENCODING = "UTF-8";
	/**
	 * Creates HTTP clients with the default timeout set.
	 */
	private static final HttpClientBuilder HTTP;
	/**
	 * The maximum amount of data to read from the request to avoid DoS attacks.
	 */
	private static final int MAX_REQUEST_LEN = 1024 * 1024;
	/**
	 * The timeout to make requests in milliseconds.
	 */
	private static final int TIMEOUT = 5000;

	static {
		// Set timeout options
		//  Static initializer blocks are undesirable, but it is really no different from
		//  creating config as another static final variable
		final RequestConfig config = RequestConfig.custom().setConnectTimeout(TIMEOUT).
			setSocketTimeout(TIMEOUT).setConnectionRequestTimeout(TIMEOUT).build();
		HTTP = HttpClientBuilder.create().setDefaultRequestConfig(config);
	}

	/**
	 * Retrieves the request body of the exchange as a string.
	 *
	 * @param exchange the HTTP request
	 * @return the request body
	 * @throws IOException if the body is too long, or if an I/O error occurs
	 */
	public static String getRequestBody(final HttpExchange exchange) throws IOException {
		int len = -1;
		// Calculate length
		final String requestLen = exchange.getRequestHeaders().getFirst("Content-Length");
		if (requestLen != null && requestLen.length() > 0) {
			try {
				len = Integer.parseInt(requestLen);
			} catch (NumberFormatException ignore) { }
		}
		if (len < 0)
			// Default length if none given
			len = BUFFER_LEN;
		if (len > MAX_REQUEST_LEN)
			throw new IOException("Request length is too long");
		// Fetch entire request body
		final InputStream is = exchange.getRequestBody();
		final ByteArrayOutputStream out = new ByteArrayOutputStream(len);
		try {
			final byte[] buffer = new byte[BUFFER_LEN];
			int read, total = 0;
			while ((read = is.read(buffer, 0, buffer.length)) > 0) {
				// Write to buffer, and avoid going over length
				total += read;
				if (total > MAX_REQUEST_LEN)
					throw new IOException("Request body is too long");
				out.write(buffer, 0, read);
			}
		} finally {
			is.close();
		}
		// Technically the input could get mojibake if it is in some other encoding, but
		// we only allow ANSI characters in the username/password anyways
		return new String(out.toByteArray(), ENCODING);
	}
	/**
	 * Makes a POST HTTP request to the specified URL with the API key provided in the
	 * Authorization header and the specified request body.
	 *
	 * @param url the URL to request
	 * @param apiKey the application's API key
	 * @param body the request body to send
	 * @return true if the request was OK, or false otherwise
	 * @throws IOException if an I/O error occurs
	 */
	public static boolean makeRequest(final String url, final String apiKey, final String body)
			throws IOException {
		boolean ok;
		final CloseableHttpClient client = HTTP.build();
		try {
			final HttpPost request = new HttpPost(url);
			// Add authorization header with API key
			request.addHeader("Authorization", "key=" + apiKey);
			request.addHeader("Content-Type", "application/json");
			if (body != null)
				request.setEntity(new StringEntity(body));
			final CloseableHttpResponse response = client.execute(request);
			try {
				ok = response.getStatusLine().getStatusCode() == HttpStatus.SC_OK;
				// Ensure request has been fully read so that it can be discarded
				EntityUtils.consume(response.getEntity());
			} finally {
				response.close();
			}
		} finally {
			client.close();
		}
		return ok;
	}
	/**
	 * Parses form data into key-value pairs.
	 *
	 * @param data the form data to parse
	 * @return the resulting keys and values
	 * @throws UnsupportedEncodingException if the configured encoding is invalid
	 */
	public static Map<String, String> parseFormData(final String data)
			throws UnsupportedEncodingException {
		if (data == null)
			throw new IllegalArgumentException("data is null");
		final String[] params = data.split("&");
		final Map<String, String> ret = new HashMap<>(params.length * 2);
		for (final String pair : params) {
			// Split up into key-value
			final int index = pair.indexOf("="), len = pair.length();
			if (index > 0 && index < len - 1) {
				// URL decode
				final String key = URLDecoder.decode(pair.substring(0, index), ENCODING);
				final String value = URLDecoder.decode(pair.substring(index + 1), ENCODING);
				ret.put(key, value);
			}
		}
		return ret;
	}
	/**
	 * Sends the response to the user.
	 *
	 * @param exchange the HTTP request
	 * @param response the response body
	 * @throws IOException if an I/O erorr occurs
	 */
	public static void sendResponse(final HttpExchange exchange, final String response)
			throws IOException {
		final byte[] data = response.getBytes(HttpUtilities.ENCODING);
		// Encode as UTF-8 and set length
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(200, data.length);
		final OutputStream os = exchange.getResponseBody();
		try {
			// Send the body to the client
			os.write(data);
		} finally {
			os.close();
		}
	}
}
