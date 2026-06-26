package com.janam.lifecycle;

import android.app.Activity;

import com.janam.util.PropertyStore;

public class ActivityPropertyStore
{
	static final PropertyStore<Activity, ActivityProperties> store = new PropertyStore<>();

	private ActivityPropertyStore()
	{
	}

	public static ActivityProperties get(Activity activity)
	{
		return store.get(activity,ActivityProperties.newInstance());
	}

	public static void put(Activity activity, ActivityProperties activityProperties)
	{
		store.put(activity, activityProperties);
	}

	public static void setActivityName(Activity activity, String name)
	{
		ActivityProperties activityProperties =  ActivityPropertyStore.get(activity);
		activityProperties.setName(name);
		ActivityPropertyStore.put(activity, activityProperties);
	}
	public static String getActivityName(Activity activity)
	{
		return get(activity).getName();
	}

	public static void setActivityState(Activity activity, ActivityState state)
	{
		ActivityProperties activityProperties =  ActivityPropertyStore.get(activity);
		activityProperties.setActivityState(state);
		ActivityPropertyStore.put(activity, activityProperties);
	}

	public static ActivityState getActivityState(Activity activity)
	{
		return get(activity).getActivityState();
	}
}
