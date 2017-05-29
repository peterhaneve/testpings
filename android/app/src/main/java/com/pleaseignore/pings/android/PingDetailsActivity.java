package com.pleaseignore.pings.android;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AppCompatDelegate;
import android.widget.TextView;

/**
 * Displays the full text of a ping when launched.
 */
public class PingDetailsActivity extends AppCompatActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_ping_details);
		// Retrieve intent extras for ping information
		final Intent intent = getIntent();
		final String pingText;
		if (intent != null)
			pingText = intent.getStringExtra(PingReceiverService.PING_KEY_MESSAGE);
		else
			pingText = null;
		// Update text, or display empty ping if it was indeed empty
		final TextView pingDetails = (TextView)findViewById(R.id.pingDetailText);
		if (pingDetails != null)
			pingDetails.setText((pingText == null) ? getString(R.string.ping_empty) : pingText);
	}
}