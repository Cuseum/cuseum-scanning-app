package com.janam.util;

import android.os.SystemClock;

/**
 * Simple stopwatch utility for measuring elapsed time
 * relative to a predefined time mark.
 */
public class StopWatch
{

	private long startTimeMs = 0L;
	private long timeMarkMs  = 0L;

	/**
	 * Starts or restarts the stopwatch.
	 */
	public void start()
	{
		startTimeMs = SystemClock.elapsedRealtime();
	}

	/**
	 * Sets a time mark relative to the start time.
	 *
	 * @param markMs time mark in milliseconds from start
	 */
	public void setTimeMark(long markMs)
	{
		this.timeMarkMs = markMs;
	}

	/**
	 * Returns the elapsed time since start.
	 *
	 * @return elapsed time in milliseconds
	 */
	public long getElapsedTime()
	{
		if (startTimeMs == 0L)
		{
			return 0L;
		}
		return SystemClock.elapsedRealtime() - startTimeMs;
	}

	/**
	 * Returns remaining time until the time mark.
	 * Returns 0 if the mark has already been passed.
	 *
	 * @return remaining time in milliseconds (never negative)
	 */
	public long getTimeRemainingUntilMark()
	{
		if (startTimeMs == 0L)
		{
			return 0L;
		}

		long elapsed   = getElapsedTime();
		long remaining = timeMarkMs - elapsed;

		return Math.max(0L, remaining);
	}

	/**
	 * Returns true if the time mark has been reached or passed.
	 */
	public boolean hasReachedMark()
	{
		return getElapsedTime() >= timeMarkMs;
	}

	/**
	 * Resets the stopwatch to an unstarted state.
	 */
	public void reset()
	{
		startTimeMs = 0L;
		timeMarkMs  = 0L;
	}
}
