package com.pleaseignore.pings.android;

import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
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
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.extensions.android.json.AndroidJsonFactory;
import com.google.api.client.http.*;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.util.GenericData;
import com.google.firebase.iid.FirebaseInstanceId;

import java.io.IOException;

/**
 * A login screen that offers login via TEST external credentials.
 */
public final class TESTLoginActivity extends AppCompatActivity implements
		HttpRequestInitializer, TextView.OnEditorActionListener {
	/**
	 * HTTP transport to use while logging in.
	 */
	public static final HttpTransport HTTP_TRANSPORT = AndroidHttp.newCompatibleTransport();
	/**
	 * Used to parse the JSON response from the server.
	 */
	public static final JsonFactory JSON_FACTORY = new AndroidJsonFactory();
	/**
	 * The timeout for login in milliseconds.
	 */
	public static final int TIMEOUT = 10000;

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
	 * Creates HTTP login requests.
	 */
	private final HttpRequestFactory requestFactory;
	/**
	 * Username field in the UI.
	 */
	private AutoCompleteTextView userText;

	public TESTLoginActivity() {
		// Set up response parser as JSON
		requestFactory = HTTP_TRANSPORT.createRequestFactory(this);
	}
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
	 * Handles a potentially successful login, storing the information as necessary.
	 *
	 * @param status the login status from the server
	 * @param userName the username used for login
	 * @return the result of the login
	 */
	private LoginResult handleLogin(final LoginStatus status, final String userName) {
		final LoginResult loggedIn = status.valid ? LoginResult.OK : LoginResult.PASSWORD;
		if (status.valid && status.challenge != null) {
			final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
				this);
			final SharedPreferences.Editor edit = prefs.edit();
			// Store login information
			edit.putString("pref_login", userName);
			edit.putString("pref_challenge", status.challenge);
			edit.apply();
		}
		return loggedIn;
	}
	@Override
	public void initialize(HttpRequest request) {
		// Server response parsed as JSON
		request.setParser(new JsonObjectParser(JSON_FACTORY));
	}
	/**
	 * Contacts the server and tries to verify the username and password. If it sticks, stores
	 * the returned challenge token.
	 *
	 * @param userName the username
	 * @param password the password
	 * @return the login status
	 */
	private LoginResult login(final String userName, final String password) {
		// Use dummy host name from the strings to log in
		final String dummyHost = getString(R.string.dummy_host), deviceID = FirebaseInstanceId.
			getInstance().getToken();
		LoginResult loggedIn = LoginResult.CONNECTION;
		if (deviceID != null)
			try {
				// Create body
				final GenericData content = new GenericData();
				content.put("username", userName);
				content.put("password", password);
				content.put("deviceID", deviceID);
				// Open connection to configured URL
				final GenericUrl loginURL = new GenericUrl("http://" + dummyHost + "/login");
				final HttpRequest request = requestFactory.buildPostRequest(loginURL,
					new UrlEncodedContent(content));
				// Set timeout
				request.setConnectTimeout(TIMEOUT);
				request.setReadTimeout(TIMEOUT);
				request.setNumberOfRetries(2);
				// Read result value and report the right status
				final HttpResponse response = request.execute();
				final LoginStatus status = response.parseAs(LoginStatus.class);
				if (status != null)
					loggedIn = handleLogin(status, userName);
			} catch (IOException e) {
				// I/O error during login process
				Log.w("Login", "Error when logging in", e);
				loggedIn = LoginResult.CONNECTION;
			}
		return loggedIn;
	}
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_testlogin);
		setupActionBar();
		// Set up the login form
		userText = (AutoCompleteTextView)findViewById(R.id.login_user);
		passwordText = (EditText)findViewById(R.id.login_pass);
		passwordText.setOnEditorActionListener(this);
		loginView = findViewById(R.id.login_form);
		progressView = findViewById(R.id.login_progress);
	}
	@Override
	public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
		if (id == R.id.login || id == EditorInfo.IME_NULL) {
			attemptLogin();
			return true;
		}
		return false;
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
	 */
	private final class UserLoginTask extends AsyncTask<Void, Void, LoginResult> {
		/**
		 * The username used to log in.
		 */
		private final String userName;
		/**
		 * The password used to log in.
		 */
		private final String password;

		protected UserLoginTask(String userName, String password) {
			this.userName = userName;
			this.password = password;
		}
		@Override
		protected LoginResult doInBackground(Void... params) {
			return login(userName, password);
		}
		@Override
		protected void onCancelled() {
			authTask = null;
			showProgress(false);
		}
		@Override
		protected void onPostExecute(final LoginResult result) {
			authTask = null;
			showProgress(false);
			if (result == null || result.equals(LoginResult.CONNECTION)) {
				// Failed to connect
				passwordText.setError(getString(R.string.error_login));
				passwordText.requestFocus();
			} else if (result.equals(LoginResult.OK))
				finish();
			else {
				// Password is wrong
				passwordText.setError(getString(R.string.error_incorrect_password));
				passwordText.requestFocus();
			}
		}
	}

	/**
	 * Communicates the status of login back to the user login task so that the right error
	 * message is displayed.
	 */
	private enum LoginResult {
		// Login OK
		OK,
		// Bad username or password
		PASSWORD,
		// Could not connect/timed out
		CONNECTION
	}
}

