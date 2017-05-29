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
	 * Key used in ping data to store the full ping text
	 */
	public static final String PING_KEY_MESSAGE = "message";

	public void onMessageReceived(final RemoteMessage message) {
		// Go to the preferences and look for sound settings
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		if (prefs.getBoolean("notifications_new_ping", true)) {
			// If pings enabled at all (should time be included as well as date?)
			final DateFormat localDate = android.text.format.DateFormat.getDateFormat(this);
			final DateFormat localTime = android.text.format.DateFormat.getTimeFormat(this);
			final NotificationManager manager = (NotificationManager)getSystemService(
				Service.NOTIFICATION_SERVICE);
			final NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
			// Create title (date in UTC but local format will change to our TZ)
			final Date utcDate = new Date(message.getSentTime());
			final String dateString = localDate.format(utcDate), timeString = localTime.
				format(utcDate), title = getString(R.string.ping_title, message.getTo(),
				dateString, timeString);
			builder.setContentTitle(title);
			// Find the full ping text
			// Later work to split out the SRP, doctrine, etc. fields
			final Map<String, String> data = message.getData();
			final String pingText;
			if (data != null && data.containsKey(PING_KEY_MESSAGE))
				pingText = data.get(PING_KEY_MESSAGE);
			else
				pingText = getString(R.string.ping_empty);
			builder.setContentText(pingText).setTicker(pingText);
			builder.setSmallIcon(R.mipmap.ic_launcher);
			// TEST cares about its pings!
			builder.setAutoCancel(false).setPriority(Notification.PRIORITY_HIGH);
			// Allow launching ping details activity on click
			final Intent detailsIntent = new Intent(this, PingDetailsActivity.class);
			detailsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			detailsIntent.putExtra(PING_KEY_MESSAGE, pingText);
			final PendingIntent launchPingDetails = PendingIntent.getActivity(this, 0,
				detailsIntent, PendingIntent.FLAG_CANCEL_CURRENT);
			builder.setContentIntent(launchPingDetails);
			builder.addAction(android.R.drawable.ic_menu_info_details, getString(
				R.string.ping_open), launchPingDetails);
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
			manager.notify((int)message.getSentTime(), builder.build());
		}
	}
}
