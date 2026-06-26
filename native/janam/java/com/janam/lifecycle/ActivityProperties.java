package com.janam.lifecycle;

public class ActivityProperties
{
	private static final String NONAME = "<none>";
	private String        name          = NONAME;
	private ActivityState      activityState = ActivityState.UNDEFINED;

	public static ActivityProperties newInstance()
	{
		return new ActivityProperties();
	}

	public ActivityProperties()
	{
		this(NONAME);
	}

	public ActivityProperties(String name)
	{
		setName(name);
	}

	public ActivityState getActivityState()
	{
		return activityState;
	}

	public ActivityProperties setActivityState(ActivityState activityState)
	{
		this.activityState = activityState;
		return this;
	}

	public String getName()
	{
		return name;
	}

	public ActivityProperties setName(String name)
	{
		this.name = (name!=null && !name.isEmpty())?name:NONAME;
		return this;
	}

}
