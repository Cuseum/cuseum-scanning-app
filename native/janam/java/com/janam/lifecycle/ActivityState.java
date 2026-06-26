package com.janam.lifecycle;

/**
 * Outputs are the same as inputs, representing events dispatched to observers
 */
public enum ActivityState
{
	UNDEFINED,
	PRE_CREATED,
	CREATED,
	POST_CREATED,
	PRE_STARTED,
	STARTED,
	POST_STARTED,
	PRE_RESUMED,
	RESUMED,
	POST_RESUMED,
	PRE_PAUSED,
	PAUSED,
	POST_PAUSED,
	PRE_STOPPED,
	STOPPED,
	POST_STOPPED,
	PRE_DESTROYED,
	DESTROYED,
	POST_DESTROYED,
	PRE_SAVE_INSTANCE_STATE,
	SAVE_INSTANCE_STATE,
	POST_SAVE_INSTANCE_STATE,


	// Merged from ScreenUserState:
	SCREEN_ON(true),
	SCREEN_OFF(true),
	USER_PRESENT(true);

	private final boolean screenUserState;

	ActivityState()
	{
		this(false);
	}

	ActivityState(boolean screenUserState)
	{
		this.screenUserState = screenUserState;
	}

	public boolean isScreenUserState()
	{
		return this.screenUserState;
	}

}
