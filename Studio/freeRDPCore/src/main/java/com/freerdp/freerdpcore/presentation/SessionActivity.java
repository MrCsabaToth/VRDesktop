/*
   Android Session Activity

   Copyright 2013 Thincast Technologies GmbH, Author: Martin Fleisz

   This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. 
   If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.freerdp.freerdpcore.presentation;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import com.freerdp.freerdpcore.R;
import com.freerdp.freerdpcore.application.GlobalApp;
import com.freerdp.freerdpcore.application.GlobalSettings;
import com.freerdp.freerdpcore.application.SessionState;
import com.freerdp.freerdpcore.domain.BookmarkBase;
import com.freerdp.freerdpcore.domain.ConnectionReference;
import com.freerdp.freerdpcore.domain.ManualBookmark;
import com.freerdp.freerdpcore.services.LibFreeRDP;
import com.freerdp.freerdpcore.utils.Mouse;

public class SessionActivity extends ActionBarActivity implements
		LibFreeRDP.UIEventListener {
	private class UIHandler extends Handler {

		public static final int REFRESH_SESSIONVIEW = 1;
		public static final int DISPLAY_TOAST = 2;
		public static final int SEND_MOVE_EVENT = 4;
		public static final int SHOW_DIALOG = 5;
		public static final int GRAPHICS_CHANGED = 6;

		UIHandler() {
			super();
		}

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
				case GRAPHICS_CHANGED: {
					sessionView.onSurfaceChange(session);
					break;
				}
				case REFRESH_SESSIONVIEW: {
					sessionView.invalidateRegion();
					break;
				}
				case DISPLAY_TOAST: {
					Toast errorToast = Toast.makeText(getApplicationContext(),
							msg.obj.toString(), Toast.LENGTH_LONG);
					errorToast.show();
					break;
				}
				case SEND_MOVE_EVENT: {
					LibFreeRDP.sendCursorEvent(session.getInstance(), msg.arg1,
							msg.arg2, Mouse.getMoveEvent());
					break;
				}
				case SHOW_DIALOG: {
					// create and show the dialog
					((Dialog) msg.obj).show();
					break;
				}
			}
		}
	}

	private class LibFreeRDPBroadcastReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			// still got a valid session?
			if (session == null)
				return;

			// is this event for the current session?
			if (session.getInstance() != intent.getExtras().getInt(
					GlobalApp.EVENT_PARAM, -1))
				return;

			switch (intent.getExtras().getInt(GlobalApp.EVENT_TYPE, -1)) {
				case GlobalApp.FREERDP_EVENT_CONNECTION_SUCCESS:
					OnConnectionSuccess(context);
					break;

				case GlobalApp.FREERDP_EVENT_CONNECTION_FAILURE:
					OnConnectionFailure(context);
					break;
				case GlobalApp.FREERDP_EVENT_DISCONNECTED:
					OnDisconnected(context);
					break;
			}
		}

		private void OnConnectionSuccess(Context context) {
			Log.v(TAG, "OnConnectionSuccess");

			// bind session
			bindSession();

			if (session.getBookmark() == null) {
				// Return immediately if we launch from URI
				return;
			}

			// add hostname to history if quick connect was used
			Bundle bundle = getIntent().getExtras();
			if (bundle != null
					&& bundle.containsKey(PARAM_CONNECTION_REFERENCE)) {
				if (ConnectionReference.isHostnameReference(bundle
						.getString(PARAM_CONNECTION_REFERENCE))) {
					assert session.getBookmark().getType() == BookmarkBase.TYPE_MANUAL;
					String item = session.getBookmark().<ManualBookmark>get()
							.getHostname();
					if (!GlobalApp.getQuickConnectHistoryGateway()
							.historyItemExists(item))
						GlobalApp.getQuickConnectHistoryGateway()
								.addHistoryItem(item);
				}
			}

			Intent cardBoardIntent = new Intent(context, ScreenActivity.class);
			startActivityForResult(cardBoardIntent, 0);
			cardBoardIntent.putExtras(bundle);
		}

		private void OnConnectionFailure(Context context) {
			Log.v(TAG, "OnConnectionFailure");

			// remove pending move events
			uiHandler.removeMessages(UIHandler.SEND_MOVE_EVENT);

			// post error message on UI thread
			if (!connectCancelledByUser)
				uiHandler.sendMessage(Message.obtain(
						null,
						UIHandler.DISPLAY_TOAST,
						getResources().getText(
								R.string.error_connection_failure)));

			closeSessionActivity(RESULT_CANCELED);
		}

		private void OnDisconnected(Context context) {
			Log.v(TAG, "OnDisconnected");

			// remove pending move events
			uiHandler.removeMessages(UIHandler.SEND_MOVE_EVENT);

			session.setUIEventListener(null);
			closeSessionActivity(RESULT_OK);
		}
	}

	public static final String PARAM_CONNECTION_REFERENCE = "conRef";
	public static final String PARAM_INSTANCE = "instance";

	private Bitmap bitmap;
	private SessionState session;
	private SessionView sessionView;

	private AlertDialog dlgVerifyCertificate;
	private AlertDialog dlgUserCredentials;
	private View userCredView;

	private UIHandler uiHandler;

	private int screen_width;
	private int screen_height;

	private boolean connectCancelledByUser = false;
	private boolean sessionRunning = false;

	private LibFreeRDPBroadcastReceiver libFreeRDPBroadcastReceiver;

	private static final String TAG = "FreeRDP.SessionActivity";

	private void createDialogs() {
		// build verify certificate dialog
		dlgVerifyCertificate = new AlertDialog.Builder(this)
				.setTitle(R.string.dlg_title_verify_certificate)
				.setPositiveButton(android.R.string.yes,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
												int which) {
								callbackDialogResult = true;
								synchronized (dialog) {
									dialog.notify();
								}
							}
						})
				.setNegativeButton(android.R.string.no,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
												int which) {
								callbackDialogResult = false;
								connectCancelledByUser = true;
								synchronized (dialog) {
									dialog.notify();
								}
							}
						}).setCancelable(false).create();

		// build the dialog
		userCredView = getLayoutInflater().inflate(R.layout.credentials, null,
				true);
		dlgUserCredentials = new AlertDialog.Builder(this)
				.setView(userCredView)
				.setTitle(R.string.dlg_title_credentials)
				.setPositiveButton(android.R.string.ok,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
												int which) {
								callbackDialogResult = true;
								synchronized (dialog) {
									dialog.notify();
								}
							}
						})
				.setNegativeButton(android.R.string.cancel,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
												int which) {
								callbackDialogResult = false;
								connectCancelledByUser = true;
								synchronized (dialog) {
									dialog.notify();
								}
							}
						}).setCancelable(false).create();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// show status bar or make fullscreen?
		if (GlobalSettings.getHideStatusBar()) {
			getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
					WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}

		this.setContentView(R.layout.session);

		Log.v(TAG, "Session.onCreate");

		// ATTENTION: We use the onGlobalLayout notification to start our
		// session.
		// This is because only then we can know the exact size of our session
		// when using fit screen
		// accounting for any status bars etc. that Android might throws on us.
		// A bit weird looking
		// but this is the only way ...
		final View activityRootView = findViewById(R.id.session_root_view);
		activityRootView.getViewTreeObserver().addOnGlobalLayoutListener(
				new OnGlobalLayoutListener() {
					@Override
					public void onGlobalLayout() {
						screen_width = activityRootView.getWidth();
						screen_height = activityRootView.getHeight();

						// start session
						if (!sessionRunning && getIntent() != null) {
							processIntent(getIntent());
							sessionRunning = true;
						}
					}
				});

		sessionView = (SessionView) findViewById(R.id.sessionView);
		sessionView.requestFocus();

		uiHandler = new UIHandler();
		libFreeRDPBroadcastReceiver = new LibFreeRDPBroadcastReceiver();

		createDialogs();

		// register freerdp events broadcast receiver
		IntentFilter filter = new IntentFilter();
		filter.addAction(GlobalApp.ACTION_EVENT_FREERDP);
		registerReceiver(libFreeRDPBroadcastReceiver, filter);
	}

	@Override
	protected void onStart() {
		super.onStart();
		Log.v(TAG, "Session.onStart");
	}

	@Override
	protected void onRestart() {
		super.onRestart();
		Log.v(TAG, "Session.onRestart");
	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.v(TAG, "Session.onResume");
	}

	@Override
	protected void onPause() {
		super.onPause();
		Log.v(TAG, "Session.onPause");
	}

	@Override
	protected void onStop() {
		super.onStop();
		Log.v(TAG, "Session.onStop");
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		Log.v(TAG, "Session.onDestroy");

		// Cancel running disconnect timers.
		GlobalApp.cancelDisconnectTimer();

		// Disconnect all remaining sessions.
		Collection<SessionState> sessions = GlobalApp.getSessions();
		for (SessionState session : sessions)
			LibFreeRDP.disconnect(session.getInstance());

		// unregister freerdp events broadcast receiver
		unregisterReceiver(libFreeRDPBroadcastReceiver);

		// free session
		GlobalApp.freeSession(session.getInstance());
		session = null;
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
	}

	private void processIntent(Intent intent) {
		// get either session instance or create one from a bookmark/uri
		Bundle bundle = intent.getExtras();
		Uri openUri = intent.getData();
		if (openUri != null) {
			// Launched from URI, e.g:
			// freerdp://user@ip:port/connect?sound=&rfx=&p=password&clipboard=%2b&themes=-
			connect(openUri);
		} else if (bundle.containsKey(PARAM_INSTANCE)) {
			int inst = bundle.getInt(PARAM_INSTANCE);
			session = GlobalApp.getSession(inst);
			bitmap = session.getSurface();
			bindSession();
		} else if (bundle.containsKey(PARAM_CONNECTION_REFERENCE)) {
			BookmarkBase bookmark = null;
			String refStr = bundle.getString(PARAM_CONNECTION_REFERENCE);
			if (ConnectionReference.isHostnameReference(refStr)) {
				bookmark = new ManualBookmark();
				bookmark.<ManualBookmark>get().setHostname(
						ConnectionReference.getHostname(refStr));
			} else if (ConnectionReference.isBookmarkReference(refStr)) {
				if (ConnectionReference.isManualBookmarkReference(refStr))
					bookmark = GlobalApp.getManualBookmarkGateway().findById(
							ConnectionReference.getManualBookmarkId(refStr));
				else
					assert false;
			}

			if (bookmark != null)
				connect(bookmark);
			else
				closeSessionActivity(RESULT_CANCELED);
		} else {
			// no session found - exit
			closeSessionActivity(RESULT_CANCELED);
		}
	}

	private void connect(BookmarkBase bookmark) {
		session = GlobalApp.createSession(bookmark, getApplicationContext());

		BookmarkBase.ScreenSettings screenSettings = session.getBookmark()
				.getActiveScreenSettings();
		Log.v(TAG, "Screen Resolution: " + screenSettings.getResolutionString());
		if (screenSettings.isAutomatic()) {
			if ((getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE) {
				// large screen device i.e. tablet: simply use screen info
				screenSettings.setHeight(screen_height);
				screenSettings.setWidth(screen_width);
			} else {
				// small screen device i.e. phone:
				// Automatic uses the largest side length of the screen and
				// makes a 16:10 resolution setting out of it
				int screenMax = (screen_width > screen_height) ? screen_width
						: screen_height;
				screenSettings.setHeight(screenMax);
				screenSettings.setWidth((int) ((float) screenMax * 1.6f));
			}
		}
		if (screenSettings.isFitScreen()) {
			screenSettings.setHeight(screen_height);
			screenSettings.setWidth(screen_width);
		}

		connectWithTitle(bookmark.getLabel());
	}

	private void connect(Uri openUri) {
		session = GlobalApp.createSession(openUri, getApplicationContext());

		connectWithTitle(openUri.getAuthority());
	}

	private void connectWithTitle(String title) {
		session.setUIEventListener(this);

		Thread thread = new Thread(new Runnable() {
			public void run() {
				session.connect();
			}
		});
		thread.start();
	}

	// binds the current session to the activity by wiring it up with the
	// sessionView and updating all internal objects accordingly
	private void bindSession() {
		Log.v(TAG, "bindSession called");
		session.setUIEventListener(this);
		sessionView.onSurfaceChange(session);
	}

	private void closeSessionActivity(int resultCode) {
		// Go back to home activity (and send intent data back to home)
		setResult(resultCode, getIntent());
		finish();
	}

	// ****************************************************************************
	// LibFreeRDP UI event listener implementation
	@Override
	public void OnSettingsChanged(int width, int height, int bpp) {

		if (bpp > 16)
			bitmap = Bitmap.createBitmap(width, height, Config.ARGB_8888);
		else
			bitmap = Bitmap.createBitmap(width, height, Config.RGB_565);

		session.setSurface(bitmap);

		if (session.getBookmark() == null) {
			// Return immediately if we launch from URI
			return;
		}

		// check this settings and initial settings - if they are not equal the
		// server doesn't support our settings
		// FIXME: the additional check (settings.getWidth() != width + 1) is for
		// the RDVH bug fix to avoid accidental notifications
		// (refer to android_freerdp.c for more info on this problem)
		BookmarkBase.ScreenSettings settings = session.getBookmark()
				.getActiveScreenSettings();
		if ((settings.getWidth() != width && settings.getWidth() != width + 1)
				|| settings.getHeight() != height
				|| settings.getColors() != bpp)
			uiHandler
					.sendMessage(Message.obtain(
							null,
							UIHandler.DISPLAY_TOAST,
							getResources().getText(
									R.string.info_capabilities_changed)));
	}

	@Override
	public void OnGraphicsUpdate(int x, int y, int width, int height) {
		LibFreeRDP.updateGraphics(session.getInstance(), bitmap, x, y, width,
				height);

		sessionView.addInvalidRegion(new Rect(x, y, x + width, y + height));

		/*
		 * since sessionView can only be modified from the UI thread any
		 * modifications to it need to be scheduled
		 */

		uiHandler.sendEmptyMessage(UIHandler.REFRESH_SESSIONVIEW);
	}

	@Override
	public void OnGraphicsResize(int width, int height, int bpp) {
		// replace bitmap
		if (bpp > 16)
			bitmap = Bitmap.createBitmap(width, height, Config.ARGB_8888);
		else
			bitmap = Bitmap.createBitmap(width, height, Config.RGB_565);
		session.setSurface(bitmap);

		/*
		 * since sessionView can only be modified from the UI thread any
		 * modifications to it need to be scheduled
		 */
		uiHandler.sendEmptyMessage(UIHandler.GRAPHICS_CHANGED);
	}

	private boolean callbackDialogResult;

	@Override
	public boolean OnAuthenticate(StringBuilder username, StringBuilder domain,
								  StringBuilder password) {
		// this is where the return code of our dialog will be stored
		callbackDialogResult = false;

		// set text fields
		((EditText) userCredView.findViewById(R.id.editTextUsername))
				.setText(username);
		((EditText) userCredView.findViewById(R.id.editTextDomain))
				.setText(domain);
		((EditText) userCredView.findViewById(R.id.editTextPassword))
				.setText(password);

		// start dialog in UI thread
		uiHandler.sendMessage(Message.obtain(null, UIHandler.SHOW_DIALOG,
				dlgUserCredentials));

		// wait for result
		try {
			synchronized (dlgUserCredentials) {
				dlgUserCredentials.wait();
			}
		} catch (InterruptedException e) {
		}

		// clear buffers
		username.setLength(0);
		domain.setLength(0);
		password.setLength(0);

		// read back user credentials
		username.append(((EditText) userCredView
				.findViewById(R.id.editTextUsername)).getText().toString());
		domain.append(((EditText) userCredView
				.findViewById(R.id.editTextDomain)).getText().toString());
		password.append(((EditText) userCredView
				.findViewById(R.id.editTextPassword)).getText().toString());

		return callbackDialogResult;
	}

	@Override
	public boolean OnGatewayAuthenticate(StringBuilder username, StringBuilder domain, StringBuilder password) {
		// this is where the return code of our dialog will be stored
		callbackDialogResult = false;

		// set text fields
		((EditText) userCredView.findViewById(R.id.editTextUsername))
				.setText(username);
		((EditText) userCredView.findViewById(R.id.editTextDomain))
				.setText(domain);
		((EditText) userCredView.findViewById(R.id.editTextPassword))
				.setText(password);

		// start dialog in UI thread
		uiHandler.sendMessage(Message.obtain(null, UIHandler.SHOW_DIALOG,
				dlgUserCredentials));

		// wait for result
		try {
			synchronized (dlgUserCredentials) {
				dlgUserCredentials.wait();
			}
		} catch (InterruptedException e) {
		}

		// clear buffers
		username.setLength(0);
		domain.setLength(0);
		password.setLength(0);

		// read back user credentials
		username.append(((EditText) userCredView
				.findViewById(R.id.editTextUsername)).getText().toString());
		domain.append(((EditText) userCredView
				.findViewById(R.id.editTextDomain)).getText().toString());
		password.append(((EditText) userCredView
				.findViewById(R.id.editTextPassword)).getText().toString());

		return callbackDialogResult;
	}

	@Override
	public int OnVerifiyCertificate(String commonName, String subject, String issuer, String fingerprint, boolean mismatch) {
		// see if global settings says accept all
		if (GlobalSettings.getAcceptAllCertificates())
			return 0;

		// this is where the return code of our dialog will be stored
		callbackDialogResult = false;

		// set message
		String msg = getResources().getString(
				R.string.dlg_msg_verify_certificate);
		msg = msg + "\n\nSubject: " + subject + "\nIssuer: " + issuer
				+ "\nFingerprint: " + fingerprint;
		dlgVerifyCertificate.setMessage(msg);

		// start dialog in UI thread
		uiHandler.sendMessage(Message.obtain(null, UIHandler.SHOW_DIALOG,
				dlgVerifyCertificate));

		// wait for result
		try {
			synchronized (dlgVerifyCertificate) {
				dlgVerifyCertificate.wait();
			}
		} catch (InterruptedException e) {
		}

		return callbackDialogResult ? 1 : 0;
	}

	@Override
	public int OnVerifyChangedCertificate(String commonName, String subject, String issuer, String fingerprint, String oldSubject, String oldIssuer, String oldFingerprint) {
		// see if global settings says accept all
		if (GlobalSettings.getAcceptAllCertificates())
			return 0;

		// this is where the return code of our dialog will be stored
		callbackDialogResult = false;

		// set message
		String msg = getResources().getString(
				R.string.dlg_msg_verify_certificate);
		msg = msg + "\n\nSubject: " + subject + "\nIssuer: " + issuer
				+ "\nFingerprint: " + fingerprint;
		dlgVerifyCertificate.setMessage(msg);

		// start dialog in UI thread
		uiHandler.sendMessage(Message.obtain(null, UIHandler.SHOW_DIALOG,
				dlgVerifyCertificate));

		// wait for result
		try {
			synchronized (dlgVerifyCertificate) {
				dlgVerifyCertificate.wait();
			}
		} catch (InterruptedException e) {
		}

		return callbackDialogResult ? 1 : 0;
	}

	@Override
	public void OnRemoteClipboardChanged(String data) {
		Log.v(TAG, "OnRemoteClipboardChanged: " + data);
	}

}
