package com.janam.lifecycle;


import com.janam.lifecycle.internal.PauseHysteresisController;
import com.janam.log.LogHelper;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * LifecycleBinder
 * <p>
 * Binds to an Activity or Application and dispatches normalized
 * Activity lifecycle events.
 * <p>
 * Guarantees:
 * - All callbacks are delivered on the UI thread
 * - Events are delivered one-by-one, not batched
 * - Only events for the bound Activity are dispatched
 * - Optional pause/resume hysteresis
 */
public final class LifecycleBinder
{
	private final String TAG = "LifecycleBinder";

	/**
	 * Observer interface
	 */
	public interface LifecycleObserver extends ScreenUserStateBinder.ScreenUserObserver
	{
		void onLifecycleStateChanged(
				Activity activity,
				ActivityState activityState
		);
	}

	private final Application             application;
	private final WeakReference<Activity> activityRef;
	private final LifecycleBindingMode    bindingMode;

	private final PauseFlushCallback        pauseFlushCallback = new PauseFlushCallback();
	private final PauseHysteresisController pauseHysteresis;

	private final ScreenStateObserver   screenStateObserver;
	private final ScreenUserStateBinder screenUserStateBinder;

	private final LifecycleCallbacks lifecycleCallbacks;

	private LifecycleObserver observer = null;

	private ActivityState currentactivityState = ActivityState.UNDEFINED;

	/**
	 * Bind to a specific Activity
	 */

	public static LifecycleBinder bind(
			Activity activity,
			LifecycleObserver observer
	)
	{
		return new LifecycleBinder(activity, activity.getApplication(), observer, LifecycleBindingMode.DROP_CALLBACKS_ON_DESTROY);
	}

	public static LifecycleBinder bind(
			Activity activity,
			LifecycleObserver observer,
			LifecycleBindingMode mode
	)
	{
		return new LifecycleBinder(activity, activity.getApplication(), observer, mode);
	}

	/**
	 * Bind to Application (all Activities)
	 */
	public static LifecycleBinder bind(
			Application application,
			LifecycleObserver observer
	)
	{
		return new LifecycleBinder(null, application, observer, LifecycleBindingMode.DROP_CALLBACKS_ON_DESTROY);
	}

	public static LifecycleBinder bind(
			Application application,
			LifecycleObserver observer,
			LifecycleBindingMode mode
	)
	{
		return new LifecycleBinder(null, application, observer, mode);
	}


	private LifecycleBinder(
			Activity activity,
			Application application,
			LifecycleObserver observer,
			LifecycleBindingMode mode
	)
	{
		if (application == null)
		{
			throw new IllegalStateException("Application required");
		}

		this.application = application;
		this.activityRef = activity != null ? new WeakReference<>(activity) : null;
		this.observer    = observer;
		this.bindingMode = mode != null ? mode : LifecycleBindingMode.DROP_CALLBACKS_ON_DESTROY;

		this.pauseHysteresis = new PauseHysteresisController(activityRef, pauseFlushCallback);
		this.pauseHysteresis.setPauseHysteresisDelay(0);

		this.lifecycleCallbacks    = new LifecycleCallbacks();
		this.screenStateObserver   = new ScreenStateObserver();
		this.screenUserStateBinder = ScreenUserStateBinder.bind(activity, this.screenStateObserver);

		this.application.registerActivityLifecycleCallbacks(lifecycleCallbacks);
	}

	/**
	 * Unbind and stop receiving lifecycle callbacks
	 */
	public void unbind()
	{
		application.unregisterActivityLifecycleCallbacks(
				lifecycleCallbacks
		);

		pauseHysteresis.reset();
		screenUserStateBinder.unbind();
	}

	public Activity getActivity()
	{
		return activityRef.get();
	}

	/**
	 * Configure pause hysteresis delay
	 *
	 * @param milliseconds delay; 0 disables hysteresis
	 */
	public void setPauseHysteresisDelay(int milliseconds)
	{
		pauseHysteresis.setPauseHysteresisDelay(milliseconds);
	}

	private Activity getBoundActivity()
	{
		if (activityRef == null)
		{
			return null;
		}

		return activityRef.get();
	}


	private void logAction(Activity activity, ActivityState input)
	{
		Activity boundActivity = getBoundActivity();
		String   activityName  = ActivityPropertyStore.getActivityName(activity);
		if (boundActivity != null)
		{
			String boundActivityName = ActivityPropertyStore.getActivityName(boundActivity);
			LogHelper.i(TAG, "input state [" + input.name() + "]: activity[" + activityName + "], boundActivityName[" + boundActivityName + "]");
		}
		else
		{
			LogHelper.i(TAG, "input state [" + input.name() + "]: activity[" + activityName + "]");
		}
	}

	private boolean inputPreFilter(Activity activity, ActivityState activityState)
	{
		if (activityState == currentactivityState)
		{
			LogHelper.d(TAG, "inputPreFilter: discard repeated state " + currentactivityState);
			return true;
		}
		if (activityState == ActivityState.SCREEN_OFF)
		{
			LogHelper.d(TAG, "inputPreFilter: got SCREEN_OFF at " + currentactivityState);
			if (currentactivityState != ActivityState.POST_SAVE_INSTANCE_STATE)
			{
				LogHelper.d(TAG, "inputPreFilter: postpone SCREEN_OFF");
				return true;
			}
		}
		return false;
	}

	private void inputPostFilter(Activity activity, ActivityState activityState)
	{
		ActivityState displayState = screenUserStateBinder.getDisplayState();

		LogHelper.d(TAG, "inputPostFilter: state = " + activityState + ", displayState= " + displayState);
		if (activityState == ActivityState.POST_SAVE_INSTANCE_STATE)
		{
			if (displayState == ActivityState.SCREEN_OFF)
			{
				LogHelper.d(TAG, "inputPostFilter: notify SCREEN_OFF");
				notifyStateChange(activity, ActivityState.SCREEN_OFF);
			}
		}
	}

	private void input(
			Activity activity,
			ActivityState activityState
	)
	{
//			logAction(activity, activityState);
		Activity boundActivity = getBoundActivity();

		if (boundActivity != null && activity != boundActivity)
		{
			return;
		}

		if (boundActivity != null)
		{
			String boundActivityName = ActivityPropertyStore.getActivityName(boundActivity);
			LogHelper.i(TAG, "input state [" + activityState.name() + "]: boundActivityName[" + boundActivityName + "]");
		}

		if (inputPreFilter(activity, activityState))
			return;

		updateCurrentActivityState(activityState);

		if (hysteresisFilter(activityState))
			return;

		LogHelper.d(TAG, "input: dispatch [" + activityState + "]");

		notifyStateChange(activity, activityState);

		inputPostFilter(activity, activityState);

		// cleanup on PRE_DESTROY. DESTROY may be overridden and POST_DESTROY is never called on the bound activity
		if (activityState == ActivityState.PRE_DESTROYED)
			cleanup(activity);
	}

	/**
	 * Dispatch a single lifecycle activityState
	 */
	private void notifyStateChange(
			final Activity activity,
			final ActivityState activityState
	)
	{
		if (observer == null)
			return;
		if (activityState.isScreenUserState())
			observer.onScreenUserStateChanged(activity, activityState);
		else
			observer.onLifecycleStateChanged(activity, activityState);
	}

	private boolean hysteresisFilter(ActivityState activityState)
	{
		if (pauseHysteresis.input(activityState))
		{
			LogHelper.d(TAG, "input: buffered");
			return true;
		}

		return false;
	}

	private void updateCurrentActivityState(ActivityState activityState)
	{
		if(!activityState.isScreenUserState())
			currentactivityState = activityState;
	}

	private void cleanup(Activity activity)
	{
		Activity boundActivity = getBoundActivity();
		if (boundActivity != null && activity == boundActivity)
		{
			if (bindingMode == LifecycleBindingMode.CANCEL_ON_DESTROY)
			{
				// Unbind everything and cancel tasks (caller needs to input task cancellation)
				unbind();
			}
			else if (bindingMode == LifecycleBindingMode.DROP_CALLBACKS_ON_DESTROY)
			{
				// Drop further callbacks: set observer to null
				// Note: pauseHysteresis still flushed? Optional to flush or clear
				observer = null; // <- need to make observer non-final for this
			}
			// NONE does nothing
		}

	}

	/**
	 * Activity lifecycle callbacks
	 */
	private final class LifecycleCallbacks
			implements Application.ActivityLifecycleCallbacks
	{

		@Override
		public void onActivityPreCreated(Activity activity, Bundle savedInstanceState)
		{
			input(activity, ActivityState.PRE_CREATED);
		}

		@Override
		public void onActivityCreated(Activity activity, Bundle savedInstanceState)
		{
			input(activity, ActivityState.CREATED);
		}

		@Override
		public void onActivityPostCreated(Activity activity, Bundle savedInstanceState)
		{
			input(activity, ActivityState.POST_CREATED);
		}

		@Override
		public void onActivityPreStarted(Activity activity)
		{
			input(activity, ActivityState.PRE_STARTED);
		}

		@Override
		public void onActivityStarted(Activity activity)
		{
			input(activity, ActivityState.STARTED);
		}

		@Override
		public void onActivityPostStarted(Activity activity)
		{
			input(activity, ActivityState.POST_STARTED);
		}

		@Override
		public void onActivityPreResumed(Activity activity)
		{
			input(activity, ActivityState.PRE_RESUMED);
		}

		@Override
		public void onActivityResumed(Activity activity)
		{
			input(activity, ActivityState.RESUMED);
		}

		@Override
		public void onActivityPostResumed(Activity activity)
		{
			input(activity, ActivityState.POST_RESUMED);
		}

		@Override
		public void onActivityPrePaused(Activity activity)
		{
			input(activity, ActivityState.PRE_PAUSED);
		}

		@Override
		public void onActivityPaused(Activity activity)
		{
			input(activity, ActivityState.PAUSED);
		}

		@Override
		public void onActivityPostPaused(Activity activity)
		{
			input(activity, ActivityState.POST_PAUSED);
		}

		@Override
		public void onActivityPreStopped(Activity activity)
		{
			input(activity, ActivityState.PRE_STOPPED);
		}

		@Override
		public void onActivityStopped(Activity activity)
		{
			input(activity, ActivityState.STOPPED);
		}

		@Override
		public void onActivityPostStopped(Activity activity)
		{
			input(activity, ActivityState.POST_STOPPED);
		}

		@Override
		public void onActivityPreDestroyed(Activity activity)
		{
			input(activity, ActivityState.PRE_DESTROYED);
		}

		@Override
		public void onActivityDestroyed(Activity activity)
		{
			input(activity, ActivityState.DESTROYED);
		}

		@Override
		public void onActivityPostDestroyed(Activity activity)
		{
			input(activity, ActivityState.POST_DESTROYED);

		}


		@Override
		public void onActivityPreSaveInstanceState(Activity activity, Bundle outState)
		{
			input(activity, ActivityState.PRE_SAVE_INSTANCE_STATE);
		}

		@Override
		public void onActivitySaveInstanceState(Activity activity, Bundle outState)
		{
			input(activity, ActivityState.SAVE_INSTANCE_STATE);
		}

		@Override
		public void onActivityPostSaveInstanceState(Activity activity, Bundle outState)
		{
			input(activity, ActivityState.POST_SAVE_INSTANCE_STATE);
		}
	}

	private class ScreenStateObserver implements ScreenUserStateBinder.ScreenUserObserver
	{

		@Override
		public void onScreenUserStateChanged(Activity activity, ActivityState state)
		{
			input(activity, state);
		}
	}

	/**
	 * Pause hysteresis flush callback
	 */
	private final class PauseFlushCallback
			implements PauseHysteresisController.Callback
	{
		@Override
		public void onFlush(List<ActivityState> activityStates)
		{
			LogHelper.d(TAG, "PauseFlushCallback: onFlush - " + activityStates.size() + " events");

			Activity boundActivity = getBoundActivity();
			if (boundActivity == null)
				return;

			for (int i = 0; i < activityStates.size(); i++)
			{
				notifyStateChange(boundActivity, activityStates.get(i));
			}
		}
	}


}
