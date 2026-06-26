package com.janam.lifecycle;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;

import androidx.annotation.NonNull;

import com.janam.log.LogHelper;

import java.lang.ref.WeakReference;
import java.util.ArrayList;


/**
 * ScreenUserStateBinder
 * <p>
 * Observes screen on/off and user present events.
 * <p>
 * Android compatibility:
 * - API 21–28: BroadcastReceiver (SCREEN_ON, SCREEN_OFF, USER_PRESENT)
 * - API 29+: DisplayManager.DisplayListener
 * <p>
 * Contract:
 * - All events delivered on UI thread
 * - Events are deduplicated
 * - No lifecycle inference
 */
public final class ScreenUserStateBinder
{
	private static final String TAG = "ScreenUserStateBinder";

	/**
	 * Observer interface
	 */
	public interface ScreenUserObserver
	{

		/**
		 * Called when screen or user state changes
		 *
		 * @param activity bound activity or null
		 * @param activityState    screen/user state
		 */
		void onScreenUserStateChanged(Activity activity, ActivityState activityState);
	}

	private final WeakReference<Activity> activityRef;
	private final ScreenUserObserver      observer;
	private final Handler                 uiHandler;

	private ActivityState displayState = ActivityState.UNDEFINED;

	private ScreenReceiver      screenReceiver = null;
	private DisplayListenerImpl displayListener = null;

	/**
	 * Bind to specific Activity
	 */
	public static ScreenUserStateBinder bind(@NonNull Activity activity, ScreenUserObserver observer)
	{
		return new ScreenUserStateBinder(activity, observer);
	}

	private ScreenUserStateBinder(@NonNull Activity activity, ScreenUserObserver observer)
	{

		this.activityRef = new WeakReference<>(activity);
		this.observer    = observer;
		this.uiHandler   = new Handler(Looper.getMainLooper());

		registerListeners();
	}

	/**
	 * Unbind and unregister listeners
	 */
	public void unbind()
	{

		if (screenReceiver != null)
		{
			screenReceiver.unregister();
			screenReceiver = null;
		}

		if (displayListener != null)
		{
			displayListener.unregister();
			displayListener = null;
		}
	}

	private Activity getBoundActivity()
	{

		if (activityRef != null)
			return activityRef.get();

		return null;
	}

	/**
	 * Register platform-appropriate listeners
	 */
	private void registerListeners()
	{

		screenReceiver = new ScreenReceiver();
		screenReceiver.register();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
		{
			displayListener = new DisplayListenerImpl();
			displayListener.register();
		}
	}

	private DisplayManager getDisplayManager()
	{
		Activity activity = getBoundActivity();
		if (activity != null)
			return (DisplayManager) activity.getSystemService(Context.DISPLAY_SERVICE);
		return null;
	}


	public ActivityState getDisplayState()
	{
		return displayState;
	}

	private boolean filterDisplayState(ActivityState activityState)
	{
		if(displayState == activityState)
		{
			LogHelper.d(TAG,"discard duplicate display state: ["+activityState+"]");
			return false;
		}
		switch (activityState)
		{
		case SCREEN_ON:
			if(displayState == ActivityState.USER_PRESENT)
			{
				LogHelper.d(TAG,"discard redundant display state: ["+activityState+"]");
				return false;
			}
			break;
		}
		displayState = activityState;
		return true;
	}


	private void processScreenStateChanged(final Activity activity, final ActivityState activityState)
	{
		if(!filterDisplayState(activityState))
			return;

		if (observer == null)
			return;

		observer.onScreenUserStateChanged(activity, activityState);
	}

	/**
	 * BroadcastReceiver implementation (API < 29)
	 */
	private final class ScreenReceiver extends BroadcastReceiver
	{
		void register()
		{
			IntentFilter      filter  = new IntentFilter();
			ArrayList<String> actions = new ArrayList<>();
			actions.add(Intent.ACTION_USER_PRESENT);
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
			{
				actions.add(Intent.ACTION_SCREEN_ON);
				actions.add(Intent.ACTION_SCREEN_OFF);
			}
			for (String action : actions)
				filter.addAction(action);
			LogHelper.d(TAG, "register screenReceiver, actions=" + actions);

			Context context = getBoundActivity();
			if (context != null)
			{
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
					context.registerReceiver(this, filter, Context.RECEIVER_EXPORTED);
				else
					context.registerReceiver(this, filter);
			}
		}

		void unregister()
		{
			try
			{
				Context context = getBoundActivity();
				if (context != null)
					context.unregisterReceiver(this);
			}
			catch (IllegalArgumentException e)
			{
			}
		}
		/**
		 * Dispatch on UI thread
		 */
		private void dispatchPost(final ActivityState state)
		{
			final Activity activity = getBoundActivity();
			uiHandler.post(new DispatchRunnable(activity, state));
		}

		/**
		 * Named Runnable
		 */

		private final class DispatchRunnable implements Runnable
		{
			private final Activity      activity;
			private final ActivityState state;

			DispatchRunnable(Activity activity, ActivityState state)
			{
				this.activity = activity;
				this.state    = state;
			}

			@Override
			public void run()
			{
				processScreenStateChanged(activity, state);
			}
		}


		@Override
		public void onReceive(Context context, Intent intent)
		{

			if (intent == null)
			{
				return;
			}

			String action = intent.getAction();

			if (action == null)
			{
				return;
			}
			LogHelper.d(TAG, "ScreenReceiver.onReceive: " + action);
			if (Intent.ACTION_SCREEN_ON.equals(action))
				dispatchPost(ActivityState.SCREEN_ON);
			else if (Intent.ACTION_SCREEN_OFF.equals(action))
				dispatchPost(ActivityState.SCREEN_OFF);// ordered via LifecycleBinder if bound
			else if (Intent.ACTION_USER_PRESENT.equals(action))
				dispatchPost(ActivityState.USER_PRESENT);

		}
	}


	/**
	 * DisplayListener implementation (API 29+)
	 */
	private final class DisplayListenerImpl implements DisplayManager.DisplayListener
	{

		private DisplayManager displayManager;

		void register()
		{
			displayManager = getDisplayManager();

			if (displayManager == null)
			{
				LogHelper.d(TAG, "register displayListener: displayManager == null");
				return;
			}

			displayManager.registerDisplayListener(this, uiHandler);
			LogHelper.d(TAG, "register displayListener: registered.");
		}

		void unregister()
		{
			LogHelper.d(TAG, "unregister displayListener...");
			if (displayManager == null)
			{
				return;
			}

			displayManager.unregisterDisplayListener(this);
		}

		@Override
		public void onDisplayAdded(int displayId)
		{
		}

		@Override
		public void onDisplayRemoved(int displayId)
		{
		}

		@Override
		public void onDisplayChanged(int displayId)
		{
			if (displayId != Display.DEFAULT_DISPLAY)
			{
				return;
			}

			Display display = displayManager.getDisplay(displayId);

			if (display == null)
			{
				return;
			}

			if (display.getState() == Display.STATE_ON)
			{
				LogHelper.d(TAG, "onDisplayChanged: SCREEN_ON");
				processScreenStateChanged(getBoundActivity(), ActivityState.SCREEN_ON);
			}
			else
			{
				LogHelper.d(TAG, "onDisplayChanged: SCREEN_OFF");
				processScreenStateChanged(getBoundActivity(), ActivityState.SCREEN_OFF);// ordered via LifecycleBinder if bound
			}

		}
	}

}
