package com.janam.lifecycle.internal;

import android.os.Handler;
import android.os.Looper;

import com.janam.log.LogHelper;
import com.janam.util.StopWatch;

import java.util.ArrayList;
import java.util.List;

abstract class StateHysteresis<T>
{
	private final String TAG = "StateHysteresis";

	public interface Listener<T>
	{
		void onFlush(List<T> states);
	}

	private final Listener<T> listener;
	private final List<T>     buffer = new ArrayList<>();

	private final StopWatch debounceStopWatch;

	private final Handler  handler         = new Handler(Looper.getMainLooper());
	private final Runnable timeoutRunnable = new Runnable()
	{
		@Override
		public void run()
		{
			if (!buffering)
			{
				return;
			}

			LogHelper.d(TAG, "timeoutRunnable: flush() after TIMEOUT");
			flush();
		}
	};

	private boolean buffering;
	private int     delayMs      = 0;
	private String  activityName = "";

	protected StateHysteresis(Listener<T> listener)
	{
		if (listener == null)
		{
			throw new IllegalArgumentException("Listener must not be null");
		}

		this.listener          = listener;
		this.debounceStopWatch = new StopWatch();
	}

	public void setDelay(int delayMs)
	{
		this.delayMs = delayMs;
		reset();
		debounceStopWatch.reset();
		debounceStopWatch.setTimeMark(delayMs);
	}

	public int getDelay()
	{
		return this.delayMs;
	}

	public boolean input(T state)
	{
		if (getDelay() > 0)
			return handle(state);
		return false;
	}

	public boolean handle(T state)
	{
		if (delayMs <= 0)
		{
			return false;
		}

		if (!buffering)
		{
			return onStart(state);
		}

		return onBuffered(state);
	}

	public int reset()
	{
		int numBuffered = buffer.size();
		handler.removeCallbacks(timeoutRunnable);
		buffer.clear();
		buffering = false;
		return numBuffered;
	}

	protected abstract boolean shouldStartBuffering(T state);
	protected abstract boolean shouldCancel(T state);
	protected abstract boolean shouldDebounce(T state);
	protected abstract boolean shouldFlush(T state);

	private boolean onStart(T state)
	{
		if (!shouldStartBuffering(state))
		{
			return false;
		}

		debounceStopWatch.start();
		buffering = true;
		buffer.add(state);

		handler.postDelayed(timeoutRunnable, delayMs);

		return true;
	}

	public void flushNow()
	{
		LogHelper.d(TAG, "flushNow: flush()");
		flush();
	}

	protected long debounce()
	{
		long elapsed = debounceStopWatch.getElapsedTime();
		int numBuffered = reset();
		LogHelper.d(TAG, "debounce " + numBuffered + " states after " + elapsed + " ms");
		return elapsed;
	}

	private boolean onBuffered(T state)
	{
		buffer.add(state);

		if (shouldCancel(state))
		{
			reset();
			return true;
		}

		if (shouldDebounce(state))
		{
			if (!debounceStopWatch.hasReachedMark())
			{
				debounce();
				return true;
			}
			LogHelper.d(TAG, "onBuffered: flush() after shouldDebounce");
			flush();
			return true;
		}

		if (shouldFlush(state))
		{
			LogHelper.d(TAG, "onBuffered: flush() after shouldFlush");
			flush();
			return true;
		}

		return true;
	}

	private void flush()
	{
		handler.removeCallbacks(timeoutRunnable);
		buffering = false;

		listener.onFlush(new ArrayList<>(buffer));
		buffer.clear();
	}
}