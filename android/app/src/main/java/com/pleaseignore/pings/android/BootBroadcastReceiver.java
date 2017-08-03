package com.pleaseignore.pings.android;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

/**
 * A broadcast receiver which starts BootService ia an intent when the boot broadcast is
 * received.
 */
public final class BootBroadcastReceiver extends BroadcastReceiver {
	/**
	 * The amount of time between challenge refreshes.
	 */
	private static final long INTERVAL = AlarmManager.INTERVAL_HOUR * 3L;

	@Override
	public void onReceive(Context context, Intent intent) {
		final String action = intent.getAction();
		// You would think that Android would prevent this...
		if (action != null && action.equals("android.intent.action.BOOT_COMPLETED")) {
			final Intent target = new Intent(context, ChallengeBroadcastReceiver.class);
			// Trigger a broadcast intent when the alarm goes off
			final PendingIntent pIntent = PendingIntent.getBroadcast(context, 1, target,
				PendingIntent.FLAG_UPDATE_CURRENT);
			final long now = SystemClock.elapsedRealtime();
			// Starting now, set alarm for interval (inexact)
			final AlarmManager alarm = (AlarmManager)context.getSystemService(Context.
				ALARM_SERVICE);
			if (alarm != null)
				// Request an alarm based on elapsed time with wakeup on to avoid missing pings
				alarm.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, now, INTERVAL,
					pIntent);
			Log.i("Challenge", "Set challenge refresh interval to " + INTERVAL);
		}
	}
}
