package com.pleaseignore.pings.android;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.Context;
import android.os.SystemClock;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;

/**
 * An IntentService which runs when the device starts up to schedule alarms.
 */
public final class BootService extends IntentService {
	/**
	 * The amount of time between challenge refreshes.
	 */
	private static final long INTERVAL = AlarmManager.INTERVAL_HOUR * 3L;

	public BootService() {
		super("BootService");
	}
	@Override
	protected void onHandleIntent(Intent intent) {
		if (intent != null) {
			setAlarm();
			WakefulBroadcastReceiver.completeWakefulIntent(intent);
		}
	}
	/**
	 * Sets an alarm upon device reboot to periodically refresh the token.
	 */
	private void setAlarm() {
		final Intent intent = new Intent(getApplicationContext(), ChallengeBroadcastReceiver.
			class);
		// Trigger a broadcast intent when the alarm goes off
		final PendingIntent pIntent = PendingIntent.getBroadcast(this, 1, intent,
			PendingIntent.FLAG_UPDATE_CURRENT);
		final long now = SystemClock.elapsedRealtime();
		// Starting now, set alarm for interval (inexact)
		final AlarmManager alarm = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
		if (alarm != null)
			// Request an alarm based on elapsed time with wakeup on to avoid missing pings
			alarm.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, now, INTERVAL,
				pIntent);
		Log.i("Challenge", "Set challenge refresh interval to " + INTERVAL);
	}
}
