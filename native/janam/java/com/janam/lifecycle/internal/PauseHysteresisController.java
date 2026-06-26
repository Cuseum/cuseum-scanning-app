package com.janam.lifecycle.internal;

import android.app.Activity;

import com.janam.lifecycle.ActivityState;
import com.janam.log.LogHelper;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * PauseHysteresisController
 * <p>
 * Adds pause/resume hysteresis to activity lifecycle events.
 * <p>
 * Behavior:
 * - Buffering begins at PRE_PAUSED
 * - Buffer is flushed at POST_RESUMED or timeout
 * - Used to suppress rapid pause/resume flapping
 */
public final class PauseHysteresisController
		extends StateHysteresis<ActivityState>
{
	private static final String TAG = "PauseHysteresis";

	private final WeakReference<Activity> activityRef;

	/**
	 * Callback invoked when buffered states are flushed
	 */
	public interface Callback
	{
		void onFlush(List<ActivityState> activityStates);
	}

	public PauseHysteresisController(
			final WeakReference<Activity> activityRef,
			final Callback callback)
	{
		super(new StateHysteresisListener(callback));
		this.activityRef = activityRef;
	}

	private Activity getActivity()
	{
		return activityRef.get();
	}

	private static class StateHysteresisListener implements Listener<ActivityState>
	{
		private final Callback callback;

		private StateHysteresisListener(Callback callback)
		{
			if (callback == null)
			{
				throw new IllegalArgumentException("Callback must not be null");
			}
			this.callback = callback;
		}

		@Override
		public void onFlush(List<ActivityState> states)
		{
			callback.onFlush(states);
		}
	}

	/**
	 * Set pause hysteresis delay.
	 *
	 * @param delayMs delay in milliseconds; 0 disables hysteresis
	 */
	public void setPauseHysteresisDelay(int delayMs)
	{
		setDelay(delayMs);
	}

	@Override
	protected boolean shouldStartBuffering(ActivityState state)
	{
		boolean ret = (state == ActivityState.PRE_PAUSED);
		LogHelper.d(TAG, "shouldStartBuffering: " + ret + " state=" + state);
		return ret;
	}

	@Override
	protected boolean shouldCancel(ActivityState state)
	{
		/*
		 * No cancellation path.
		 * Once buffering begins, we either flush or timeout.
		 */
		return false;
	}

	@Override
	protected boolean shouldDebounce(ActivityState state)
	{
		boolean ret = (state == ActivityState.POST_RESUMED);
		LogHelper.d(TAG, "shouldDebounce: " + ret + " state=" + state);
		return ret;
	}

	@Override
	protected boolean shouldFlush(ActivityState state)
	{
		boolean ret;

		switch (state)
		{
		case POST_STOPPED:
		case PRE_DESTROYED:
			ret = true;
			break;

		default:
			ret = false;
		}

		LogHelper.d(TAG, "shouldFlush: " + ret + " state=" + state);
		return ret;
	}
}