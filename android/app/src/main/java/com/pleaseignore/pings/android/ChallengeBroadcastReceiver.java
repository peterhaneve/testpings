package com.pleaseignore.pings.android;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;

/**
 * A broadcast receiver which starts ChallengeAcceptedService via an intent when the alarm
 * broadcast is received.
 */
public final class ChallengeBroadcastReceiver extends WakefulBroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		final Intent newIntent = new Intent(context, ChallengeAcceptedService.class);
		startWakefulService(context, newIntent);
	}
}
