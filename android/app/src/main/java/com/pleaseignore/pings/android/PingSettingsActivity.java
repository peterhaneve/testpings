package com.pleaseignore.pings.android;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.*;
import android.support.v7.app.AppCompatDelegate;
import android.text.TextUtils;
import android.view.MenuItem;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.*;

/**
 * The main user-visible activity of this class consists of only ping preferences.
 */
public class PingSettingsActivity extends AppCompatPreferenceActivity implements
		OnSuccessListener<Void> {
	/**
	 * A preference value change listener that updates the preference's summary
	 * to reflect its new value.
	 */
	private static Preference.OnPreferenceChangeListener onChangeListener = new Preference.
			OnPreferenceChangeListener() {
		@Override
		public boolean onPreferenceChange(Preference preference, Object value) {
			final String stringValue = value.toString();
			if (preference instanceof ListPreference) {
				// For list preferences, look up the correct display value in
				// the preference's 'entries' list
				final ListPreference listPreference = (ListPreference)preference;
				int index = listPreference.findIndexOfValue(stringValue);
				// Set the summary to reflect the new value
				preference.setSummary(index >= 0 ? listPreference.getEntries()[index] : null);
			} else if (preference instanceof RingtonePreference) {
				// For sound preferences, look up the correct display value using
				// RingtoneManager
				if (TextUtils.isEmpty(stringValue))
					// Empty values correspond to 'silent' (no sound)
					preference.setSummary(R.string.pref_sound_silent);
				else {
					final Ringtone ringtone = RingtoneManager.getRingtone(preference.
						getContext(), Uri.parse(stringValue));
					if (ringtone == null)
						// Clear the summary if there was a lookup error
						preference.setSummary(null);
					else
						// Set the summary to reflect the new ringtone display name
						preference.setSummary(ringtone.getTitle(preference.getContext()));
				}
			} else
				// For all other preferences, set the summary to the value's
				// simple string representation
				preference.setSummary(stringValue);
			return true;
		}
	};

	/**
	 * Binds a preference's summary to its value. More specifically, when the
	 * preference's value is changed, its summary (line of text below the
	 * preference title) is updated to reflect the value. The summary is also
	 * immediately updated upon calling this method. The exact display format is
	 * dependent on the type of preference.
	 *
	 * @see #onChangeListener
	 */
	private static void bindPreferenceSummaryToValue(Preference preference) {
		// Set the listener to watch for value changes
		preference.setOnPreferenceChangeListener(onChangeListener);
		// Trigger the listener immediately with the preference's current value
		onChangeListener.onPreferenceChange(preference, PreferenceManager.
			getDefaultSharedPreferences(preference.getContext()).getString(preference.getKey(),
			""));
	}
	/**
	 * Reports true if Google Play services are available, and false otherwise.
	 *
	 * @param activity the context to check for services
	 * @param with the code to be executed when the services are available
	 */
	public static void ensureGooglePlayAvailable(final Activity activity,
												 OnSuccessListener<Void> with) {
		final GoogleApiAvailability avail = GoogleApiAvailability.getInstance();
		if (avail.isGooglePlayServicesAvailable(activity) == ConnectionResult.SUCCESS) {
			// Services are already available, execute listener now
			if (with != null)
				with.onSuccess(null);
		} else {
			// Make services available, and execute if successful
			final Task<Void> result = avail.makeGooglePlayServicesAvailable(activity);
			if (with != null)
				result.addOnSuccessListener(with);
		}
	}
	/**
	 * Helper method to determine if the device has an extra-large screen. For
	 * example, 10" tablets are extra-large.
	 */
	private static boolean isXLargeTablet(Context context) {
		return (context.getResources().getConfiguration().screenLayout
			& Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
	}

	/**
	 * This method stops fragment injection in malicious applications.
	 * Make sure to deny any unknown fragments here.
	 */
	protected boolean isValidFragment(String fragmentName) {
		return PreferenceFragment.class.getName().equals(fragmentName) ||
			GeneralPreferenceFragment.class.getName().equals(fragmentName) ||
			NotificationPreferenceFragment.class.getName().equals(fragmentName);
	}
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public void onBuildHeaders(List<Header> target) {
		loadHeadersFromResource(R.xml.pref_headers, target);
	}
	protected void onCreate(Bundle savedInstanceState) {
		AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO);
		super.onCreate(savedInstanceState);
		ensureGooglePlayAvailable(this, this);
	}
	public boolean onIsMultiPane() {
		return isXLargeTablet(this);
	}
	protected void onResume() {
		super.onResume();
		ensureGooglePlayAvailable(this, null);
	}
	public void onSuccess(Void ignore) { }

	/**
	 * Shows only the general preferences, which contain login preferences and the about box.
	 */
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public static class GeneralPreferenceFragment extends PreferenceFragment {
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.pref_general);
			setHasOptionsMenu(true);
			final Preference loginPref = findPreference("pref_login");
			loginPref.setSummary("Not logged in");
			loginPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				public boolean onPreferenceClick(Preference preference) {
					// Start a login activity
					final Intent intent = new Intent(getActivity(), TESTLoginActivity.class);
					startActivity(intent);
					return true;
				}
			});
			// Update summary of about to the current build version
			findPreference("pref_about").setSummary(BuildConfig.APPLICATION_ID + " " +
				BuildConfig.VERSION_NAME);
		}
		public boolean onOptionsItemSelected(MenuItem item) {
			int id = item.getItemId();
			if (id == android.R.id.home) {
				// Home button
				startActivity(new Intent(getActivity(), PingSettingsActivity.class));
				return true;
			}
			return super.onOptionsItemSelected(item);
		}
	}

	/**
	 * Shows notification preferences only for a given group.
	 */
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public static class NotificationPreferenceFragment extends PreferenceFragment {
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.pref_notification);
			setHasOptionsMenu(true);
			// Bind the summaries of EditText/List/Dialog/Ringtone preferences to their values
			bindPreferenceSummaryToValue(findPreference("notifications_new_ping_sound"));
		}
		public boolean onOptionsItemSelected(MenuItem item) {
			final int id = item.getItemId();
			if (id == android.R.id.home) {
				// Home button
				startActivity(new Intent(getActivity(), PingSettingsActivity.class));
				return true;
			}
			return super.onOptionsItemSelected(item);
		}
	}
}
