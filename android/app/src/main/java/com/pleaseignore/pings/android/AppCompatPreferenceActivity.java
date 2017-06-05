package com.pleaseignore.pings.android;

import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.support.annotation.LayoutRes;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.widget.Toolbar;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * A {@link android.preference.PreferenceActivity} which implements and proxies the necessary
 * calls to be used with AppCompat
 */
public abstract class AppCompatPreferenceActivity extends PreferenceActivity {
	private AppCompatDelegate delegate;

	public void addContentView(View view, ViewGroup.LayoutParams params) {
		getDelegate().addContentView(view, params);
	}
	private AppCompatDelegate getDelegate() {
		if (delegate == null) {
			delegate = AppCompatDelegate.create(this, null);
			delegate.applyDayNight();
		}
		return delegate;
	}
	public MenuInflater getMenuInflater() {
		return getDelegate().getMenuInflater();
	}
	public ActionBar getSupportActionBar() {
		return getDelegate().getSupportActionBar();
	}
	public void setSupportActionBar(@Nullable Toolbar toolbar) {
		getDelegate().setSupportActionBar(toolbar);
	}
	public void invalidateOptionsMenu() {
		getDelegate().invalidateOptionsMenu();
	}
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		getDelegate().onConfigurationChanged(newConfig);
	}
	protected void onCreate(Bundle savedInstanceState) {
		getDelegate().installViewFactory();
		getDelegate().onCreate(savedInstanceState);
		super.onCreate(savedInstanceState);
	}
	protected void onDestroy() {
		super.onDestroy();
		getDelegate().onDestroy();
	}
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		getDelegate().onPostCreate(savedInstanceState);
	}
	protected void onPostResume() {
		super.onPostResume();
		getDelegate().onPostResume();
	}
	protected void onStop() {
		super.onStop();
		getDelegate().onStop();
	}
	protected void onTitleChanged(CharSequence title, int color) {
		super.onTitleChanged(title, color);
		getDelegate().setTitle(title);
	}
	public void setContentView(@LayoutRes int layoutResID) {
		getDelegate().setContentView(layoutResID);
	}
	public void setContentView(View view) {
		getDelegate().setContentView(view);
	}
	public void setContentView(View view, ViewGroup.LayoutParams params) {
		getDelegate().setContentView(view, params);
	}
}
