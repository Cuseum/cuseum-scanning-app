package com.janam.log;

import android.annotation.SuppressLint;
import android.util.Log;

import androidx.annotation.NonNull;

import com.janam.util.BinUtils;

public class LogHelper
{
	public static final int LOGPRIORITY_ASSERT  = Log.ASSERT;        //7
	public static final int LOGPRIORITY_ERROR   = Log.ERROR;        //6
	public static final int LOGPRIORITY_WARN    = Log.WARN;            //5
	public static final int LOGPRIORITY_INFO    = Log.INFO;            //4
	public static final int LOGPRIORITY_DEBUG   = Log.DEBUG;        //3
	public static final int LOGPRIORITY_VERBOSE = Log.VERBOSE;        //2

	private static String appPrefix   = "APP";                // we are GT1 unless NFCSDK
	private static Writer writer      = LogHelper::defaultWriter;    // using Log functions
	private static int    logPriority = LOGPRIORITY_VERBOSE;    // we are verbose unless set otherwise

	private static final String[] logPriorityString = new String[]{"0", "1", "VERBOSE", "DEBUG", "INFO", "WARN", "ERROR", "ASSERT"};

	@FunctionalInterface
	public interface Writer
	{
		void write(int logPriority, String TAG, String message);
	}

	private LogHelper()
	{

	}

	public static void setWriter(Writer writer)
	{
		LogHelper.writer = writer;
	}

	public static Writer getWriter()
	{
		return writer;
	}

	public static void setAppPrefix(String appPrefix)
	{
		LogHelper.appPrefix = appPrefix;
	}

	public static String getAppPrefix()
	{
		return appPrefix;
	}

	public static int getLogPriority()
	{
		return logPriority;
	}

	public static void setLogPriority(int logPriority)
	{
		if (logPriority > LOGPRIORITY_ASSERT)
			logPriority = LOGPRIORITY_ASSERT;
		if (logPriority < 0)
			logPriority = 0;

		LogHelper.logPriority = logPriority;

		println(LOGPRIORITY_ASSERT, "LOGGING", "Priority level set to " + logPriorityString[logPriority]);
	}

	public static void setVerbose(boolean verbose)
	{
		setLogPriority(verbose ? LOGPRIORITY_VERBOSE : LOGPRIORITY_INFO);
	}

	public static boolean isVerbose()
	{
		return isLoggable(LOGPRIORITY_VERBOSE);
	}

	public static boolean isDebug()
	{
		return isLoggable(LOGPRIORITY_DEBUG);
	}


	public static boolean isLoggable(int logPriority)
	{
		if (logPriority < LogHelper.logPriority)
			return false;
		return true;
	}

	public static void i(String TAG, String message)
	{
		println(LOGPRIORITY_INFO, TAG, message);
	}

	public static void i(String TAG, String pattern, Object... args)
	{
		i(TAG, MessageFormatter.format(pattern, args));
	}

	public static void d(String TAG, String message)
	{
		println(LOGPRIORITY_DEBUG, TAG, message);
	}

	public static void d(String TAG, String pattern, Object... args)
	{
		d(TAG, MessageFormatter.format(pattern, args));
	}

	public static void w(String TAG, String message)
	{
		println(LOGPRIORITY_WARN, TAG, message);
	}

	public static void w(String TAG, String pattern, Object... args)
	{
		w(TAG, MessageFormatter.format(pattern, args));
	}

	public static void e(String TAG, String message)
	{
		println(LOGPRIORITY_ERROR, "ERR-" + TAG, message);
	}

	public static void e(String TAG, String pattern, Object... args)
	{
		e(TAG, MessageFormatter.format(pattern, args));
	}


	public static void v(String TAG, String message)
	{
		println(LOGPRIORITY_VERBOSE, TAG, message);
	}

	public static void v(String TAG, String pattern, Object... args)
	{
		v(TAG, MessageFormatter.format(pattern, args));
	}

	public static void a(String TAG, String message)
	{
		println(LOGPRIORITY_ASSERT, TAG, message);
	}

	public static void a(String TAG, String pattern, Object... args)
	{
		a(TAG, MessageFormatter.format(pattern, args));
	}

	@SuppressLint("DefaultLocale")
	private static String formatHexDump(String message, byte[] bytes, int length)
	{
		StringBuilder sb = new StringBuilder();
		sb.append(message);
		sb.append(BinUtils.toHexString(true, bytes, 0, length));
		return sb.toString();
	}

	private static String formatHexDump(String message, byte[] bytes)
	{
		return formatHexDump(message, bytes, bytes.length);
	}

	public static void aHexDump(String TAG, String message, byte[] bytes, int length)
	{
		println(LOGPRIORITY_ASSERT, TAG, formatHexDump(message, bytes, length));
	}

	public static void aHexDump(String TAG, String message, byte[] bytes)
	{
		println(LOGPRIORITY_ASSERT, TAG, formatHexDump(message, bytes));
	}

	public static void vHexDump(String TAG, String message, byte[] bytes, int length)
	{
		println(LOGPRIORITY_VERBOSE, TAG, formatHexDump(message, bytes, length));
	}

	public static void vHexDump(String TAG, String message, byte[] bytes)
	{
		println(LOGPRIORITY_VERBOSE, TAG, formatHexDump(message, bytes));
	}

	public static void dHexDump(String TAG, String message, byte[] bytes, int length)
	{
		println(LOGPRIORITY_DEBUG, TAG, formatHexDump(message, bytes, length));
	}

	public static void dHexDump(String TAG, String message, byte[] bytes)
	{
		println(LOGPRIORITY_DEBUG, TAG, formatHexDump(message, bytes));
	}

	public static void iHexDump(String TAG, String message, byte[] bytes, int length)
	{
		println(LOGPRIORITY_INFO, TAG, formatHexDump(message, bytes, length));
	}

	public static void iHexDump(String TAG, String message, byte[] bytes)
	{
		println(LOGPRIORITY_INFO, TAG, formatHexDump(message, bytes));
	}

	public static void wHexDump(String TAG, String message, byte[] bytes, int length)
	{
		println(LOGPRIORITY_WARN, TAG, formatHexDump(message, bytes, length));
	}

	public static void wHexDump(String TAG, String message, byte[] bytes)
	{
		println(LOGPRIORITY_WARN, TAG, formatHexDump(message, bytes));
	}

	public static void eHexDump(String TAG, String message, byte[] bytes, int length)
	{
		println(LOGPRIORITY_ERROR, TAG, formatHexDump(message, bytes, length));
	}

	public static void eHexDump(String TAG, String message, byte[] bytes)
	{
		println(LOGPRIORITY_ERROR, TAG, formatHexDump(message, bytes));
	}

	public static void printStackTrace(String TAG, Throwable throwable)
	{
		e(TAG, Log.getStackTraceString(throwable));
	}


	public static void println(int logPriority, String tag, String message)
	{
		//observe our settings
		if (!isLoggable(logPriority))
			return;

		if (writer == null) // set back to default
			writer = LogHelper::defaultWriter;

		if (appPrefix == null)
			appPrefix = "APP";

		if (message == null)
			message = "(null)";

		StringBuilder sb = new StringBuilder();
		sb.append(appPrefix);
		sb.append("-");
		sb.append(Thread.currentThread().getName());
		if (tag != null && !tag.isEmpty())
		{
			sb.append("/");
			sb.append(tag);
		}

		writer.write(logPriority, sb.toString(), message);

	}

	private static void defaultWriter(int logPriority, @NonNull String tag, @NonNull String message)
	{
		Log.println(logPriority, tag, message);
	}

}
