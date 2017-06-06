package com.pleaseignore.pings.android;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;

/**
 * A broadcast receiver which starts BootService ia an intent when the boot broadcast is
 * received.
 */
public final class BootBroadcastReceiver extends WakefulBroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		final Intent newIntent = new Intent(context, BootService.class);
		startWakefulService(context, newIntent);
	}
}
