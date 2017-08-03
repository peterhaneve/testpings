package com.pleaseignore.pings.android;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.support.v7.app.NotificationCompat;
import android.text.TextUtils;
import android.util.Log;
import com.google.api.client.http.*;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.util.GenericData;

import java.io.IOException;

/**
 * A periodically executing task run by the alarm manager that, if logged in, periodically
 * refreshes the challenge token with the server to keep receiving pings.
 */
public final class ChallengeAcceptedService extends IntentService implements
		HttpRequestInitializer {
	/**
	 * Creates HTTP login requests.
	 */
	private final HttpRequestFactory requestFactory;

	public ChallengeAcceptedService() {
		super("ChallengeAcceptedService");
		// Set up response parser as JSON
		requestFactory = TESTLoginActivity.HTTP_TRANSPORT.createRequestFactory(this);
	}
	/**
	 * If a valid challenge token is present, presents it to the server to refresh the interval
	 * for which this device continues to receive pings.
	 */
	private void challengeAccepted() {
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		final ConnectivityManager conn = (ConnectivityManager)getSystemService(
			Context.CONNECTIVITY_SERVICE);
		final String dummyHost = getString(R.string.dummy_host);
		// Retrieve current settings for username and challenge
		final String username = prefs.getString("pref_login", ""), challenge = prefs.getString(
			"pref_challenge", "");
		boolean connected = true;
		if (conn != null) {
			// Are we connected? Do not try to hit the server if no connection available
			final NetworkInfo info = conn.getActiveNetworkInfo();
			connected = info != null && info.isAvailable() && info.isConnected();
		}
		// If username and challenge are defined, try to get a response
		if (!connected)
			Log.i("Challenge", "No network connection, not updating challenge");
		else if (challenge != null && username != null && challenge.length() > 0 &&
				username.length() > 0)
			try {
				// Create body
				final GenericData content = new GenericData();
				content.put("username", username);
				content.put("challenge", challenge);
				// Open connection to configured URL
				final GenericUrl refreshURL = new GenericUrl("http://" + dummyHost + "/refresh");
				final HttpRequest request = requestFactory.buildPostRequest(refreshURL,
					new UrlEncodedContent(content));
				// Set timeout
				request.setConnectTimeout(TESTLoginActivity.TIMEOUT);
				request.setReadTimeout(TESTLoginActivity.TIMEOUT);
				request.setNumberOfRetries(2);
				// Read result value and report the right status
				final HttpResponse response = request.execute();
				final LoginStatus status = response.parseAs(LoginStatus.class);
				if (status != null)
					if (status.valid)
						Log.i("Challenge", "Challenge refreshed");
					else {
						// If valid, continue on; if not valid, reset the login information
						final SharedPreferences.Editor edit = prefs.edit();
						edit.remove("pref_login");
						edit.remove("pref_challenge");
						Log.i("Challenge", "Challenge no longer valid, logging out");
						edit.apply();
						notifyExpired();
					}
			} catch (IOException e) {
				// Error, log it but do not annoy the user
				Log.w("Challenge", "Error when refreshing challenge token", e);
			}
	}
	@Override
	public void initialize(HttpRequest request) {
		// Server response parsed as JSON
		request.setParser(new JsonObjectParser(TESTLoginActivity.JSON_FACTORY));
	}
	/**
	 * Notifies the user that the challenge token has expired.
	 */
	private void notifyExpired() {
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		final NotificationCompat.Builder builder = PingReceiverService.createNotification(this,
			getString(R.string.ping_expired), getString(R.string.error_expired));
		// Allow launching ping settings activity on click
		final Intent detailsIntent = new Intent(this, PingSettingsActivity.class);
		detailsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		final PendingIntent launchPingDetails = PendingIntent.getActivity(this, 0,
			detailsIntent, PendingIntent.FLAG_CANCEL_CURRENT);
		builder.setContentIntent(launchPingDetails);
		// Retrieve system notification manager to show popup
		final NotificationManager manager = (NotificationManager)getSystemService(
			Service.NOTIFICATION_SERVICE);
		manager.notify(0, builder.build());
	}
	@Override
	protected void onHandleIntent(Intent intent) {
		if (intent != null) {
			// Go refresh the token
			challengeAccepted();
			WakefulBroadcastReceiver.completeWakefulIntent(intent);
		}
	}
}
