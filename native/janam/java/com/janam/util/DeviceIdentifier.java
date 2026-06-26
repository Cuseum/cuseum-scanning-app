package com.janam.util;

import android.os.Build;

import java.util.Objects;

public class DeviceIdentifier
{
	public static String modelName;
	public static String manufacturer;

	static
	{
		modelName    = Build.MODEL.toUpperCase();
		manufacturer = Build.MANUFACTURER.toUpperCase();
	}

	private DeviceIdentifier()
	{
	}

	public static boolean isJanam()
	{
		return manufacturer.startsWith("JANAM");
	}

	public static boolean isXT2()
	{
		return isJanam() && Objects.equals(modelName, "XT2");
	}

	public static boolean isXT3()
	{
		return isJanam() && Objects.equals(modelName, "XT3");
	}

	public static boolean isXT4()
	{
		return isJanam() && Objects.equals(modelName, "XT4");
	}

	public static boolean isXT30()
	{
		return isJanam() && Objects.equals(modelName, "XT30");
	}

	public static boolean isXT40()
	{
		return isJanam() && Objects.equals(modelName, "XT40");
	}

	public static boolean isXR2()
	{
		return isJanam() && Objects.equals(modelName, "XR2");
	}

	public static boolean isXG4()
	{
		return isJanam() && Objects.equals(modelName, "XG4");
	}

	public static boolean isXG5()
	{
		return isJanam() && Objects.equals(modelName, "XG5");
	}

}
