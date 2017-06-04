package com.pleaseignore.pings.android;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.TextView;
import com.google.firebase.iid.FirebaseInstanceId;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * A login screen that offers login via TEST external credentials.
 */
public final class TESTLoginActivity extends AppCompatActivity {
	/**
	 * The encoding to use for all requests.
	 */
	private static final String ENCODING = "UTF-8";

	/**
	 * Is this username potentially valid?
	 *
	 * @param username the user name to check
	 * @return whether it might be plausibly correct (does not use network to verify)
	 */
	private static boolean isUserValid(String username) {
		return username.length() > 2;
	}
	/**
	 * Is this password potentially valid?
	 *
	 * @param password the password to check
	 * @return whether it might be plausibly correct (does not use network to verify)
	 */
	private static boolean isPasswordValid(String password) {
		// TODO Are there other restrictions placed on TEST external passwords?
		return password.length() > 2;
	}

	/**
	 * The current login task so that it can be cancelled.
	 */
	private UserLoginTask authTask;
	/**
	 * Parent form of username and password to be hidden during the sign in process.
	 */
	private View loginView;
	/**
	 * Password field in the UI.
	 */
	private EditText passwordText;
	/**
	 * Progress bar shown during login.
	 */
	private View progressView;
	/**
	 * Username field in the UI.
	 */
	private AutoCompleteTextView userText;

	/**
	 * Attempts to sign in or register the account specified by the login form.
	 * If there are form errors (invalid email, missing fields, etc.), the
	 * errors are presented and no actual login attempt is made.
	 */
	private void attemptLogin() {
		if (authTask == null) {
			userText.setError(null);
			passwordText.setError(null);
			// Store values at the time of the login attempt
			final String user = userText.getText().toString();
			final String password = passwordText.getText().toString();
			boolean cancel = false;
			View focusView = null;
			// Sanity checking for password
			if (!TextUtils.isEmpty(password) && !isPasswordValid(password)) {
				passwordText.setError(getString(R.string.error_invalid_password));
				focusView = passwordText;
				cancel = true;
			}
			// Sanity checking for username
			if (TextUtils.isEmpty(user)) {
				userText.setError(getString(R.string.error_field_required));
				focusView = userText;
				cancel = true;
			} else if (!isUserValid(user)) {
				userText.setError(getString(R.string.error_invalid_user));
				focusView = userText;
				cancel = true;
			}
			if (cancel)
				// Do not attempt login
				focusView.requestFocus();
			else {
				// Show progress spinner during auth process
				showProgress(true);
				authTask = new UserLoginTask(user, password);
				authTask.execute((Void)null);
			}
		}
	}
	/**
	 * Contacts the server and tries to verify the username and password. If it sticks, stores
	 * the returned challenge token.
	 *
	 * @param userName the username
	 * @param password the password
	 * @return true if logged in, or false if invalid
	 */
	private boolean login(final String userName, final String password) {
		// TODO Switch to TEST Auth
		final String dummyHost = getString(R.string.dummy_host), deviceID = FirebaseInstanceId.
			getInstance().getToken();
		boolean loggedIn = false;
		if (deviceID != null)
			try {
				// Open connection to configured URL
				final String loginURL = "http://" + dummyHost + "/login";
				final HttpURLConnection conn = (HttpURLConnection)new URL(loginURL).
					openConnection();
				// Allow 10 seconds to connect
				conn.setConnectTimeout(10000);
				conn.setReadTimeout(5000);
				conn.setRequestMethod("POST");
				conn.setDoOutput(true);
				// Create body
				final String body = "username=" + URLEncoder.encode(userName, ENCODING) +
					"&password=" + URLEncoder.encode(password, ENCODING) +
					"&deviceID=" + URLEncoder.encode(deviceID, ENCODING);
				// Send login data
				final OutputStream os = conn.getOutputStream();
				os.write(body.getBytes(ENCODING));
				os.close();
				// Read result value
				if (conn.getResponseCode() == 200) {
					final BufferedReader br = new BufferedReader(new InputStreamReader(
						conn.getInputStream()));
					try {
						// Read first line from request and check if it is a challenge ID
						final String line = br.readLine();
						loggedIn = line != null && line.length() > 12;
					} finally {
						br.close();
					}
				}
				conn.disconnect();
			} catch (IOException e) {
				Log.e("Login", "Error when logging in", e);
				loggedIn = false;
			}
		return loggedIn;
	}
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_testlogin);
		setupActionBar();
		// Set up the login form
		userText = (AutoCompleteTextView)findViewById(R.id.login_user);
		passwordText = (EditText)findViewById(R.id.login_pass);
		passwordText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
			public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
				if (id == R.id.login || id == EditorInfo.IME_NULL) {
					attemptLogin();
					return true;
				}
				return false;
			}
		});
		loginView = findViewById(R.id.login_form);
		progressView = findViewById(R.id.login_progress);
	}
	public void onLogin(final View source) {
		attemptLogin();
	}
	/**
	 * Set up the {@link android.app.ActionBar}, if the API is available.
	 */
	private void setupActionBar() {
		final ActionBar bar = getSupportActionBar();
		if (bar != null)
			bar.setDisplayHomeAsUpEnabled(true);
	}
	/**
	 * Shows the progress UI and hides the login form.
	 */
	private void showProgress(final boolean show) {
		progressView.setVisibility(show ? View.VISIBLE : View.GONE);
		loginView.setVisibility(show ? View.GONE : View.VISIBLE);
	}

	/**
	 * Performs TEST external authentication in the background.
	 *
	 * This stub to be filled in with actual TEST login details.
	 */
	public final class UserLoginTask extends AsyncTask<Void, Void, Boolean> {
		private final String userName;
		private final String password;

		protected UserLoginTask(String userName, String password) {
			this.userName = userName;
			this.password = password;
		}
		protected Boolean doInBackground(Void... params) {
			return login(userName, password);
		}
		protected void onCancelled() {
			authTask = null;
			showProgress(false);
		}
		protected void onPostExecute(final Boolean success) {
			authTask = null;
			showProgress(false);
			if (success)
				finish();
			else {
				// Password is wrong
				passwordText.setError(getString(R.string.error_incorrect_password));
				passwordText.requestFocus();
			}
		}
	}
}

