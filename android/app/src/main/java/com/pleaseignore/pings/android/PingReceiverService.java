package com.pleaseignore.pings.android;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.v7.app.NotificationCompat;
import android.text.TextUtils;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.text.*;
import java.util.*;

/**
 * A service which receives and notifies the user about pings.
 */
public class PingReceiverService extends FirebaseMessagingService {
	/**
	 * Key used in ping data to store the ping group name
	 */
	public static final String PING_KEY_GROUP = "group";
	/**
	 * Key used in ping data to store the full ping text
	 */
	public static final String PING_KEY_MESSAGE = "message";

	public void onMessageReceived(final RemoteMessage message) {
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		// If pings enabled at all
		if (prefs.getBoolean("notifications_new_ping", true)) {
			// Find the full ping text
			// Later work to split out the SRP, doctrine, etc. fields
			final Map<String, String> data = message.getData();
			final String pingText;
			if (data != null && data.containsKey(PING_KEY_MESSAGE))
				pingText = data.get(PING_KEY_MESSAGE);
			else
				pingText = getString(R.string.ping_empty);
			// Find the target ping group
			final String group;
			if (data != null && data.containsKey(PING_KEY_GROUP))
				group = data.get(PING_KEY_GROUP);
			else
				group = getString(R.string.ping_all);
			// Retrieve system notification manager to show popup
			final NotificationManager manager = (NotificationManager)getSystemService(
				Service.NOTIFICATION_SERVICE);
			final long time = message.getSentTime();
			manager.notify((int)time, makeNotification(new Date(time), group, pingText));
		}
	}
	/**
	 * Prepares a ping notification for the user.
	 *
	 * @param dateOf the date the ping was sent, UTC
	 * @param group the group target of the ping (English text, not group ID)
	 * @param pingText the full text of the ping
	 */
	private Notification makeNotification(final Date dateOf, final String group,
										  final String pingText) {
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		// Use local format for date and time
		final DateFormat localDate = android.text.format.DateFormat.getDateFormat(this);
		final DateFormat localTime = android.text.format.DateFormat.getTimeFormat(this);
		final NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
		// Create ping title
		final String dateString = localDate.format(dateOf), timeString = localTime.
			format(dateOf), title = getString(R.string.ping_title, group, dateString,
			timeString);
		builder.setContentTitle(title);
		builder.setContentText(pingText).setTicker(pingText);
		builder.setSmallIcon(R.mipmap.ic_launcher);
		// TEST cares about its pings!
		builder.setAutoCancel(true).setPriority(Notification.PRIORITY_HIGH);
		// Allow launching ping details activity on click
		final Intent detailsIntent = new Intent(this, PingDetailsActivity.class);
		detailsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		detailsIntent.putExtra(PING_KEY_MESSAGE, pingText);
		final PendingIntent launchPingDetails = PendingIntent.getActivity(this, 0,
			detailsIntent, PendingIntent.FLAG_CANCEL_CURRENT);
		builder.setContentIntent(launchPingDetails);
		// Set up sound if not silent, and vibrate if necessary
		final String sound = prefs.getString("notifications_new_ping_sound", "");
		if (!TextUtils.isEmpty(sound))
			builder.setSound(Uri.parse(sound));
		int defaults = 0;
		if (prefs.getBoolean("notifications_new_ping_vibrate", false))
			defaults |= Notification.DEFAULT_VIBRATE;
		if (prefs.getBoolean("notifications_new_ping_light", false))
			defaults |= Notification.DEFAULT_LIGHTS;
		builder.setDefaults(defaults);
		// All pings get their own notification (spam?), so use the sent time as the key
		return builder.build();
	}
}
