package com.janam.lifecycle;

public enum LifecycleBindingMode
{
	/**
	 * No lifecycle binding. The task is not tied to any lifecycle events.
	 */
	NONE,

	/**
	 * Cancel the task when the associated activity is destroyed.
	 */
	CANCEL_ON_DESTROY,

	/**
	 * Drop any further callbacks when the associated activity is destroyed.
	 */
	DROP_CALLBACKS_ON_DESTROY
}