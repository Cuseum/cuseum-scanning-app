package com.janam.janamdevicesdk;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.janam.device.common.DevInfoIndex;
import com.janam.device.common.ScanConst;
import com.janam.device.common.ScanConst.LightMode;
import com.janam.device.common.ScanConst.SymbologyID;
import com.janam.device.common.ScanConst.TriggerMode;

import com.janam.device.sdk.ScanManager;
import com.janam.lifecycle.ActivityState;
import com.janam.lifecycle.LifecycleBinder;
import com.janam.lifecycle.LifecycleBindingMode;
import com.janam.lifecycle.ScreenUserStateBinder;
import com.janam.log.LogHelper;

public class JanamScanner
{
	final static String TAG = "JanamScanner";

	private boolean scannerAutoEnable = false;  // true = auto-enable at startup

	public class ResultTypeConfig
	{
		/**
		 * For the JanamScanner wrapper class, resultType can only be one of the Intent result types:
		 * 				ScanConst.ResultType.DCD_RESULT_USERMSG,
		 * 				ScanConst.ResultType.DCD_RESULT_EVENT, or
		 * 				ResultTypeConfig.DCD_RESULT_CUSTOM
		 */
		public boolean useExistingResultEvent = true;  // true = observe exiting resultType
		public int     resultType             = ScanConst.ResultType.DCD_RESULT_USERMSG; // default to USERMSG

		// DCD_RESULT_EVENT defined values & DCD_RESULT_USERMSG default values
		public String intentCategory              = Intent.CATEGORY_DEFAULT;
		public String intentAction                = ScanConst.INTENT_EVENT;
		public String EXTRA_EVENT_DECODE_RESULT   = ScanConst.EXTRA_EVENT_DECODE_RESULT;
		public String EXTRA_EVENT_DECODE_LENGTH   = ScanConst.EXTRA_EVENT_DECODE_LENGTH;
		public String EXTRA_EVENT_DECODE_VALUE    = ScanConst.EXTRA_EVENT_DECODE_VALUE;
		public String EXTRA_EVENT_DECODE_LETTER   = ScanConst.EXTRA_EVENT_DECODE_LETTER;
		public String EXTRA_EVENT_DECODE_MODIFIER = ScanConst.EXTRA_EVENT_DECODE_MODIFIER;
		public String EXTRA_EVENT_DECODE_TIME     = ScanConst.EXTRA_EVENT_DECODE_TIME;
		public String EXTRA_EVENT_SYMBOL_NAME     = ScanConst.EXTRA_EVENT_SYMBOL_NAME;
		public String EXTRA_EVENT_SYMBOL_ID       = ScanConst.EXTRA_EVENT_SYMBOL_ID;
		public String EXTRA_EVENT_SYMBOL_TYPE     = ScanConst.EXTRA_EVENT_SYMBOL_TYPE;

		public static final int DCD_RESULT_CUSTOM = 4;  // result type missing from the device SDK

	}


	// local values to keep track of scanner settings through lifecycle events
	private boolean wantScanningEnabled             = true;
	private boolean wantScanBeepEnabled             = true;
	private boolean wantScanLEDEnabled              = true;
	private boolean wantScanVibrateEnabled          = false;
	private int     wantScanVibratorSuccessInterval = 250;
	private int     wantScanVibratorFailInterval    = 0;
	private boolean wantPhoneModeEnabled            = false;
	private boolean wantAimerOn                     = true;
	private boolean wantIllumOn                     = true;
	private boolean wantContinuousScan              = false;
	private boolean wantReadFailNotification        = false;

	private final Context      context;
	private       ScanManager  scanManager  = null;
	private       ScanReceiver scanReceiver = null;


	private final ActivityLifeCycleObserver activityLifeCycleObserver = new ActivityLifeCycleObserver();
	private final LifecycleBinder           activityLifecycleBinder;
	private final boolean                   autoLifeCycle;

	private boolean          registerIntent   = true;      // true = we register for an intent
	private ResultTypeConfig resultTypeConfig = new ResultTypeConfig();

	public static JanamScanner createInstance(Activity activity)
	{
		return createInstance(activity,true);
	}

	public static JanamScanner createInstance(Activity activity, boolean autoLifeCycle)
	{
		LogHelper.i(TAG, "newInstance:...");
		JanamScanner instance = new JanamScanner(activity, autoLifeCycle);
		instance.enableScanning(instance.scannerAutoEnable);
		LogHelper.i(TAG, "newInstance: done.");
		return instance;
	}

	private JanamScanner(Activity activity, boolean autoLifeCycle)
	{
		this.context       = activity;
		this.autoLifeCycle = autoLifeCycle;

		if (autoLifeCycle)
		{
			activityLifecycleBinder = LifecycleBinder.bind(activity, activityLifeCycleObserver, LifecycleBindingMode.NONE);
			// prevent application lifecycle pause/resume flicker sometimes caused by stuff briefly popping up over our activity.
			// We'll handle pause only after we've been out of the foreground for an amount of time.
			// com.janam.access.sdk.AccessController sets itself up like this.
			activityLifecycleBinder.setPauseHysteresisDelay(500);
		}
		else
			activityLifecycleBinder = null;
	}

	// activity states
	public enum EngineState
	{
		DISABLED,
		ENABLED,
	}

	// encapsulated class holding Scan result
	public static class DecodeResult
	{
		private com.janam.device.common.DecodeResult result;

		private DecodeResult(com.janam.device.common.DecodeResult result)
		{
			this.result = result;
		}

		public int getDecodeLength()
		{
			return result.decodeLength;
		}

		public byte[] getDecodeValue()
		{
			return result.decodeValue;
		}

		public String getDecodeString()
		{
			return new String(getDecodeValue());
		}

		public String getSymName()
		{
			return result.symName;
		}

		public byte getSymId()
		{
			return result.symId;
		}

		public int getSymType()
		{
			return result.symType;
		}

		public byte getLetter()
		{
			return result.letter;
		}

		public byte getModifier()
		{
			return result.modifier;
		}

		public int getDecodeTimeMillisecond()
		{
			return result.decodeTimeMillisecond;
		}

		@NonNull
		@Override
		public String toString()
		{
			return result.toString();
		}
	}

	public interface ScanReceiver
	{
		public void onScanDecode(DecodeResult decodeResult);
	}

	// getter/setter for ResultypeConfig accessible by app
	protected ResultTypeConfig getResultTypeConfig()
	{
		return resultTypeConfig;
	}

	protected void setResultTypeConfig(ResultTypeConfig resultTypeConfig)
	{
		this.resultTypeConfig = resultTypeConfig;
	}


	public void setScanReceiver(ScanReceiver scanReceiver)
	{
		this.scanReceiver = scanReceiver;
	}

	private void notifySDecodeResult(DecodeResult decodeResult)
	{
		if (scanReceiver != null)
		{
			try
			{
				scanReceiver.onScanDecode(decodeResult);
			}
			catch (Exception ignored)
			{
			}
		}
	}

	public interface ScanEngineStateChangeListener
	{
		public void onScanEngineStateChanged(EngineState newstate, EngineState prevstate);
	}

	private ScanEngineStateChangeListener scanEngineStateChangeListener = null;

	public void setScanEngineStateChangeListener(ScanEngineStateChangeListener scanEngineStateChangeListener)
	{
		this.scanEngineStateChangeListener = scanEngineStateChangeListener;
	}

	private void notiftScanEngineStateChanged(EngineState newstate, EngineState prevstate)
	{
		if (scanEngineStateChangeListener != null)
		{
			try
			{
				scanEngineStateChangeListener.onScanEngineStateChanged(newstate, prevstate);
			}
			catch (Exception ignored)
			{
			}
		}
	}

	public EngineState getScanEngineState()
	{
		return getTriggerEnable() ? EngineState.ENABLED : EngineState.DISABLED;
	}


	// application control of scanning enable
	public void enableScanning(boolean enabled)
	{
		LogHelper.d(TAG, "enableScanning: " + enabled);
		wantScanningEnabled = enabled;
		if (scanManager == null)
		{
			return;
		}
		//required for correct operation
		// enable scanning permanenently, then enable/disable the trigger at the application level
		// for faster response time
		if (scanManager.aDecodeGetDecodeEnable() != DevInfoIndex.ENABLED)
		{
			LogHelper.i(TAG, "enableScanning: enable decode");
			scanManager.aDecodeSetDecodeEnable(DevInfoIndex.ENABLED);
		}

		// turn on/off using TriggerEnable
		setTriggerEnable(enabled);
	}

	// application control of scanning enable
	public void enableContinuousScanning(boolean enabled)
	{
		wantContinuousScan = enabled;
		if (scanManager == null)
		{
			return;
		}

		LogHelper.i(TAG, "enableContinuousScanning: " + (enabled ? "CONTINUOUS" : "TRIGGER"));
		// set trigger mode
		scanManager.aDecodeSetTriggerMode(enabled ? TriggerMode.DCD_TRIGGER_MODE_CONTINUOUS : TriggerMode.DCD_TRIGGER_MODE_ONESHOT);
	}

	public void enableReadFailNotification(boolean enabled)
	{
		wantReadFailNotification = enabled;
	}

	// Cuseum: read/force the scanner result type so we can guarantee broadcast-only output
	// (no clipboard / no keyboard wedge). DCD_RESULT_COPYPASTE copies each scan to the Android
	// clipboard and DCD_RESULT_KBDMSG emulates keystrokes; DCD_RESULT_USERMSG delivers only the
	// intent broadcast this wrapper listens for.
	public int getResultType()
	{
		return scanManager != null ? scanManager.aDecodeGetResultType() : -1;
	}

	public void useBroadcastResultOnly()
	{
		resultTypeConfig.useExistingResultEvent = false;
		resultTypeConfig.resultType             = ScanConst.ResultType.DCD_RESULT_USERMSG;
		if (scanManager != null && scanManager.aDecodeGetResultType() != ScanConst.ResultType.DCD_RESULT_USERMSG)
		{
			scanManager.aDecodeSetResultType(ScanConst.ResultType.DCD_RESULT_USERMSG);
		}
	}

	// application manual trigger control
	public void triggerScanning(boolean flag)
	{
		if (scanManager == null)
		{
			return;
		}
		LogHelper.i(TAG, "triggerScanning: " + (flag ? "ON" : "OFF"));
		scanManager.aDecodeSetTriggerOn(DevInfoIndex.OFF); // force OFF before ON
		if (flag)
			scanManager.aDecodeSetTriggerOn(DevInfoIndex.ON);
	}

	private void initializeScannerSettings()
	{
		LogHelper.d(TAG, "initializeScannerSettings:");
		if (scanManager == null)
		{
			LogHelper.d(TAG, "initializeScannerSettings: scanManager is NULL");
			return;
		}

		if (scanManager.aDecodeGetTriggerMode() != ScanConst.TriggerMode.DCD_TRIGGER_MODE_ONESHOT)
			scanManager.aDecodeSetTriggerMode(ScanConst.TriggerMode.DCD_TRIGGER_MODE_ONESHOT);


		if (resultTypeConfig.useExistingResultEvent)
		{
			int resultType = scanManager.aDecodeGetResultType();
			switch (resultType)
			{
			case ScanConst.ResultType.DCD_RESULT_USERMSG:
			case ScanConst.ResultType.DCD_RESULT_EVENT:
			case ResultTypeConfig.DCD_RESULT_CUSTOM:
				resultTypeConfig.resultType = resultType;
				break;
			default:
				// we can only use intent result types. anything else we have to take over
				LogHelper.w(TAG,"ResultType "+resultType+" cannot be used for this app. Setting to "+resultTypeConfig.resultType);
				resultTypeConfig.useExistingResultEvent = false;
				break;
			}
		}

		if(!resultTypeConfig.useExistingResultEvent)
		{
			LogHelper.d(TAG, "setDecodeResultType");
			// set to what we configured as fallback
			if (scanManager.aDecodeGetResultType() != resultTypeConfig.resultType)
				scanManager.aDecodeSetResultType(resultTypeConfig.resultType);
		}

		LogHelper.d(TAG, "setDecodeEnable");
		//required for correct operation
		// enable scanning permanenently, then enable/disable the trigger at the application level
		// for faster response time
		scanManager.aDecodeSetDecodeEnable(DevInfoIndex.ENABLED);
		scanManager.aDecodeSetTriggerEnable(DevInfoIndex.DISABLED);

		// setup Symbologies
		LogHelper.d(TAG, "setup Symbologies");

		ScanConst.SymbologyID.class.getFields();
		for (Field f : ScanConst.SymbologyID.class.getFields())
		{
			try
			{
				int id    = f.getInt(f);
				int state = DevInfoIndex.UNKNOWN;
				switch (id)
				{
				case SymbologyID.DCD_SYM_NIL:
				case SymbologyID.DCD_SYM_LAST:
					break;
				// Cuseum: enable every symbology the scanning app reads
				// (membership cards + pairing codes). See app/home.tsx, pair.tsx, scan-result.tsx.
				case SymbologyID.DCD_SYM_CODE128:
				case SymbologyID.DCD_SYM_PDF417:
				case SymbologyID.DCD_SYM_QR:
				case SymbologyID.DCD_SYM_AZTEC:
				case SymbologyID.DCD_SYM_DATAMATRIX:
				case SymbologyID.DCD_SYM_EAN13:
				case SymbologyID.DCD_SYM_EAN8:
				case SymbologyID.DCD_SYM_UPCA:
				case SymbologyID.DCD_SYM_UPCE:
				case SymbologyID.DCD_SYM_CODE39:
				case SymbologyID.DCD_SYM_CODE93:
					state = DevInfoIndex.ENABLED;
					break;
				default:
					// defer to external scan settings
//						state = DevInfoIndex.DISABLED; // disable all other barcode types
					break;
				}
				if (state != DevInfoIndex.UNKNOWN)
				{
					if (scanManager.aDecodeSymGetEnable(id) != state)
					{
						scanManager.aDecodeSymSetEnable(id, state);
					}
				}

			}
			catch (IllegalAccessException e)
			{
			}
		}

		// some decode settings
		LogHelper.d(TAG, "setScanBeepEnabled to " + wantScanBeepEnabled);
		setScanBeepEnabled(wantScanBeepEnabled);

		LogHelper.d(TAG, "setScanLEDEnabled to " + wantScanLEDEnabled);
		setScanLEDEnabled(wantScanLEDEnabled);

		LogHelper.d(TAG, "setScanVibrateEnabled to " + wantScanVibrateEnabled);
		setScanVibrateEnabled(wantScanVibrateEnabled);

		LogHelper.d(TAG, "setScanVibratorSuccessInterval to " + wantScanVibratorSuccessInterval);
		setScanVibratorSuccessInterval(wantScanVibratorSuccessInterval);

		LogHelper.d(TAG, "setScanVibratorFailInterval to " + wantScanVibratorFailInterval);
		setScanVibratorFailInterval(wantScanVibratorFailInterval);

		LogHelper.d(TAG, "setPhoneMode to " + wantPhoneModeEnabled);
		setPhoneMode(wantPhoneModeEnabled);

		LogHelper.d(TAG, "setAimerOn to " + wantAimerOn);
		setAimerOn(wantAimerOn);

		LogHelper.d(TAG, "setIllumOn to " + wantIllumOn);
		setIllumOn(wantIllumOn);

		LogHelper.d(TAG, "enableContinuousScanning to " + wantContinuousScan);
		enableContinuousScanning(wantContinuousScan);

		LogHelper.d(TAG, "aDecodeSetTerminator:");
		scanManager.aDecodeSetTerminator(ScanConst.Terminator.DCD_TERMINATOR_NONE);

		LogHelper.d(TAG, "initializeScannerSettings: done");

	}


	// application control of Scan beep
	public boolean getScanBeepEnabled()
	{
		if (scanManager == null)
		{
			return wantScanBeepEnabled;
		}
		return (scanManager.aDecodeGetBeepEnable() == DevInfoIndex.ENABLED);
	}

	public void setScanBeepEnabled(boolean value)
	{
		wantScanBeepEnabled = value;
		if (scanManager == null)
		{
			return;
		}
		if (getScanBeepEnabled() != value)
		{
			scanManager.aDecodeSetBeepEnable(value ? DevInfoIndex.ENABLED : DevInfoIndex.DISABLED);
		}
	}

	// application control of Scan vibrator
	public boolean getScanVibrateEnabled()
	{
		if (scanManager == null)
		{
			return wantScanVibrateEnabled;
		}
		return (scanManager.aDecodeGetVibratorEnable() == DevInfoIndex.ENABLED);
	}

	public void setScanVibrateEnabled(boolean value)
	{
		wantScanVibrateEnabled = value;
		if (scanManager == null)
		{
			return;
		}
		if (getScanVibrateEnabled() != value)
		{
			scanManager.aDecodeSetVibratorEnable(value ? DevInfoIndex.ENABLED : DevInfoIndex.DISABLED);
		}
	}

	public int getScanVibratorFailInterval()
	{
		if (scanManager == null)
		{
			return wantScanVibratorFailInterval;
		}
		return scanManager.aDecodeGetVibratorSuccessInterval();
	}

	public void setScanVibratorFailInterval(int value)
	{
		wantScanVibratorFailInterval = value;
		if (scanManager == null)
		{
			return;
		}
		if (getScanVibratorFailInterval() != value)
		{
			scanManager.aDecodeSetVibratorFailInterval(value);
		}
	}

	public int getScanVibratorSuccessInterval()
	{
		if (scanManager == null)
		{
			return wantScanVibratorSuccessInterval;
		}
		return scanManager.aDecodeGetVibratorSuccessInterval();
	}

	public void setScanVibratorSuccessInterval(int value)
	{
		wantScanVibratorSuccessInterval = value;
		if (scanManager == null)
		{
			return;
		}
		if (getScanVibratorSuccessInterval() != value)
		{
			scanManager.aDecodeSetVibratorSuccessInterval(value);
		}
	}


	// application control of Scan LED
	public boolean getScanLEDEnabled()
	{
		if (scanManager == null)
		{
			return wantScanLEDEnabled;
		}
		return (scanManager.aDecodeGetLedEnable() == DevInfoIndex.ENABLED);
	}

	public void setScanLEDEnabled(boolean value)
	{
		wantScanLEDEnabled = value;
		if (scanManager == null)
		{
			return;
		}
		if (getScanLEDEnabled() != value)
		{
			scanManager.aDecodeSetLedEnable(value ? DevInfoIndex.ENABLED : DevInfoIndex.DISABLED);
		}
	}

	// application control of scanning phone/display mode filter
	public boolean getPhoneMode()
	{
		if (scanManager == null)
		{
			return wantPhoneModeEnabled;
		}
		return (scanManager.aDecodeGetPhoneDisplayMode() == DevInfoIndex.ENABLED);
	}

	public void setPhoneMode(boolean value)
	{
		wantPhoneModeEnabled = value;
		if (scanManager == null)
		{
			return;
		}
		if (getPhoneMode() != value)
		{
			scanManager.aDecodeSetPhoneDisplayMode(value ? DevInfoIndex.ENABLED : DevInfoIndex.DISABLED);
		}
	}


	// application control of scanning aimer dot
	public boolean isAimerOn()
	{
		if (scanManager == null)
		{
			return wantAimerOn;
		}
		return ((scanManager.aDecodeImageGetLightMode() & LightMode.DCD_LIGHT_MODE_AIM_ON) == LightMode.DCD_LIGHT_MODE_AIM_ON);
	}

	public void setAimerOn(boolean value)
	{
		LogHelper.d(TAG, "setAimerOn:");
		wantAimerOn = value;
		if (scanManager == null)
		{
			LogHelper.d(TAG, "setAimerOn: scanManager is NULL");
			return;
		}
//		if(isAimerOn() != value)
		{
			LogHelper.d(TAG, "setAimerOn: aDecodeImageGetLightMode");
			int mode = scanManager.aDecodeImageGetLightMode();
			if (value)
			{
				mode |= LightMode.DCD_LIGHT_MODE_AIM_ON;
			}
			else
			{
				mode &= ~LightMode.DCD_LIGHT_MODE_AIM_ON;
			}
			LogHelper.d(TAG, "setAimerOn: aDecodeImageSetLightMode");
			scanManager.aDecodeImageSetLightMode(mode);
		}
		LogHelper.d(TAG, "setAimerOn: done");
	}

	// application control of scan illumination
	public boolean isIllumOn()
	{
		if (scanManager == null)
		{
			return wantIllumOn;
		}
		return ((scanManager.aDecodeImageGetLightMode() & LightMode.DCD_LIGHT_MODE_ILLUM_ON) == LightMode.DCD_LIGHT_MODE_ILLUM_ON);
	}

	public void setIllumOn(boolean value)
	{
		wantIllumOn = value;
		if (scanManager == null)
		{
			return;
		}

		int mode = scanManager.aDecodeImageGetLightMode();
		if (value)
		{
			mode |= LightMode.DCD_LIGHT_MODE_ILLUM_ON;
		}
		else
		{
			mode &= ~LightMode.DCD_LIGHT_MODE_ILLUM_ON;
		}
		scanManager.aDecodeImageSetLightMode(mode);
	}

	// internal control of trigger
	private boolean getTriggerEnable()
	{
		if (scanManager == null)
		{
			return false;
		}
		return (scanManager.aDecodeGetTriggerEnable() == DevInfoIndex.ENABLED);
	}

	private void setTriggerEnable(boolean value)
	{
		if (scanManager == null)
		{
			return;
		}
		boolean triggerEnabled = getTriggerEnable();
		if (triggerEnabled != value)
		{
			scanManager.aDecodeSetTriggerEnable(value ? DevInfoIndex.ENABLED : DevInfoIndex.DISABLED);
			notiftScanEngineStateChanged(value ? EngineState.ENABLED : EngineState.DISABLED, value ? EngineState.DISABLED : EngineState.ENABLED);
			LogHelper.i(TAG, "setTriggerEnable: enabled = " + getTriggerEnable());
		}
		else
		{
			LogHelper.i(TAG, "setTriggerEnable: trigger already set to = " + triggerEnabled);
		}
	}

	// scan result message handler
	private class ScanResultReceiver extends BroadcastReceiver
	{
		private Handler handler = new Handler(Looper.getMainLooper());

		@SuppressLint("UnspecifiedRegisterReceiverFlag")
		private void register()
		{
			String intentAction   = ScanConst.INTENT_USERMSG;
			String intentCategory = null;
			if (registerIntent)
			{
				if (resultTypeConfig.useExistingResultEvent)
				{
					resultTypeConfig.resultType = scanManager.aDecodeGetResultType();
					switch (resultTypeConfig.resultType)
					{
					case ScanConst.ResultType.DCD_RESULT_USERMSG:
						intentAction = ScanConst.INTENT_USERMSG;
						break;
					case ScanConst.ResultType.DCD_RESULT_EVENT:
						intentAction = ScanConst.INTENT_EVENT;
						intentCategory = Intent.CATEGORY_DEFAULT;
						break;
					case ResultTypeConfig.DCD_RESULT_CUSTOM:
						intentAction = resultTypeConfig.intentAction;
						intentCategory = resultTypeConfig.intentCategory;
						break;
					default:
						registerIntent = false;
						break;
					}
				}
				else
				{
					if (scanManager.aDecodeGetResultType() != resultTypeConfig.resultType)
						scanManager.aDecodeSetResultType(resultTypeConfig.resultType);
				}
			}

			if (registerIntent)
			{
				IntentFilter intentFilter = new IntentFilter();
				intentFilter.addAction(intentAction);
				if (intentCategory != null)
					intentFilter.addCategory(intentCategory);

				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
					context.registerReceiver(scanResultReceiver, intentFilter, Context.RECEIVER_EXPORTED);
				else
					context.registerReceiver(scanResultReceiver, intentFilter);
			}
		}

		private void unregister()
		{
			context.unregisterReceiver(this);
		}

		private void delayedEnableTrigger(int milliseconds)
		{
			handler.postDelayed(new Runnable()
			{
				@Override
				public void run()
				{
					setTriggerEnable(true);
				}
			}, milliseconds);
		}

		private void delayedPullTrigger(int milliseconds)
		{
			handler.postDelayed(new Runnable()
			{
				@Override
				public void run()
				{
					setTriggerEnable(true);
					triggerScanning(true);
				}
			}, milliseconds);
		}

		@Override
		public void onReceive(Context context, Intent intent)
		{
			com.janam.device.common.DecodeResult decodeResult = new com.janam.device.common.DecodeResult();
			scanManager.aDecodeGetResult(decodeResult);

			String action = intent.getAction();

			if (resultTypeConfig.resultType == ScanConst.ResultType.DCD_RESULT_USERMSG
					    && Objects.equals(action, ScanConst.INTENT_USERMSG))
			{
				scanManager.aDecodeGetResult(decodeResult);
			}
			else if (resultTypeConfig.resultType == ScanConst.ResultType.DCD_RESULT_EVENT
					         && Objects.equals(action, ScanConst.INTENT_EVENT))
			{
				getDecodeResultFromIntent(decodeResult, intent);
			}
			else if (resultTypeConfig.resultType == ResultTypeConfig.DCD_RESULT_CUSTOM
					         && Objects.equals(action, resultTypeConfig.intentAction))
			{
				getDecodeResultFromIntent(decodeResult, intent);
			}
			else
				return; // not intersted

			// remove Scan terminator, Aim ID, symbol ID from the barcode string
			filterResult(decodeResult);

			triggerScanning(false);
			setTriggerEnable(false); // disable until after callback

			boolean deliverNotification = true;
			if (decodeResult.symType == SymbologyID.DCD_SYM_NIL && !wantReadFailNotification)
				deliverNotification = false; // filter out "READ FAIL"

			if (deliverNotification)
			{
				notifySDecodeResult(new DecodeResult(decodeResult)); // callback
			}

			decodeResult = null;
			// if scanning still needed after callback re-enable trigger
			if (wantContinuousScan && wantScanningEnabled)
			{
				delayedPullTrigger(2000);
			}
			else if ((scannerAutoEnable || wantScanningEnabled) && (!getTriggerEnable()))
			{
				delayedEnableTrigger(250);
			}

		}

		private void getDecodeResultFromIntent(com.janam.device.common.DecodeResult decodeResult, Intent intent)
		{
			Bundle      bundle = intent.getExtras();
			Set<String> keys   = bundle.keySet(); // this is just here to force bundle.toString() to dump its guts in the debugger
			/**
			 * "Bundle[{
			 * EXTRA_EVENT_DECODE_STRING_VALUE=070942002387,
			 * EXTRA_EVENT_DECODE_MODIFIER=48,                                               byte    DecodeResult.modifier
			 * EXTRA_EVENT_SOURCE=dev.scanner.imager,
			 * EXTRA_EVENT_SYMBOL_NAME=UPC A,                                                String  DecodeResult.symName
			 * EXTRA_EVENT_SYMBOL_TYPE=56,                                                   int     DecodeResult.symType (SymbologyID.DCD_SYM_UPCA)
			 * EXTRA_EVENT_DECODE_VALUE=[48, 55, 48, 57, 52, 50, 48, 48, 50, 51, 56, 55],    byte[]  DecodeResult.decodeValue
			 * EXTRA_EVENT_DECODE_TIME=50,                                                   int     DecodeResult.decodeTimeMillisecond
			 * EXTRA_EVENT_DECODE_LENGTH=12,                                                 int     DecodeResult.decodeLength
			 * EXTRA_EVENT_DECODE_LETTER=69,                                                 byte    DecodeResult.letter
			 * EXTRA_EVENT_DECODE_RESULT=true,                                               boolean result
			 * EXTRA_EVENT_SYMBOL_ID=99                                                      byte     DecodeResult.symId
			 * }]"
			 */

			// there is a boolean result in the bundle that we don't use for anything
			boolean result = bundle.getBoolean(resultTypeConfig.EXTRA_EVENT_DECODE_RESULT);

			decodeResult.decodeLength          = bundle.getInt(resultTypeConfig.EXTRA_EVENT_DECODE_LENGTH, 0);
			decodeResult.decodeValue           = bundle.getByteArray(resultTypeConfig.EXTRA_EVENT_DECODE_VALUE);
			decodeResult.symName               = bundle.getString(resultTypeConfig.EXTRA_EVENT_SYMBOL_NAME);
			decodeResult.symId                 = bundle.getByte(resultTypeConfig.EXTRA_EVENT_SYMBOL_ID);
			decodeResult.symType               = bundle.getInt(resultTypeConfig.EXTRA_EVENT_SYMBOL_TYPE);
			decodeResult.letter                = bundle.getByte(resultTypeConfig.EXTRA_EVENT_DECODE_LETTER);
			decodeResult.modifier              = bundle.getByte(resultTypeConfig.EXTRA_EVENT_DECODE_MODIFIER);
			decodeResult.decodeTimeMillisecond = bundle.getInt(resultTypeConfig.EXTRA_EVENT_DECODE_TIME, 0);

		}
		private void filterResult(com.janam.device.common.DecodeResult decodeResult)
		{
			// with transmit barcode id disabled, transmit AimId disabled, and terminator = DCD_TERMINATOR_NONE, we have:
			// decodeValue = [48, 55, 48, 57, 52, 50, 48, 48, 50, 51, 56, 55]
			// strBarcode = "070942002387"

			// with transmit barcode id enabled, transmit AimId enabled, and terminator = DCD_TERMINATOR_TAB_LF, we have:
			// decodeValue =               [99, 93, 69, 48, 48, 55, 48, 57, 52, 50, 48, 48, 50, 51, 56, 55, 9, 10]
			// strBarcode =                "c]E0070942002387\t\n"
			// 0: decodeResult.symId(99)    ^
			// 1: Aim ID indicator(])       -^
			// 2: decodeResult.letter(69)   --^
			// 3: decodeResult.modifier(48) ---^
			// (decodeResult.decodeLength -2) = TAB
			// (decodeResult.decodeLength -1) = LF

			// with transmit barcode id disabled, transmit AimId enabled, and terminator = DCD_TERMINATOR_TAB_LF, we have:
			// strBarcode = "]E0070942002387\t\n"
			// decodeValue = [93, 69, 48, 48, 55, 48, 57, 52, 50, 48, 48, 50, 51, 56, 55, 9, 10]

			// with transmit barcode id enabled, transmit AimId disabled, and terminator = DCD_TERMINATOR_NONE, we have:
			// decodeValue = [99, 48, 55, 48, 57, 52, 50, 48, 48, 50, 51, 56, 55]
			// strBarcode = "c070942002387"

			int trimFromStart = 0;
			int trimFromEnd   = 0;

			int transmitId = scanManager.aDecodeGetResultSymIdEnable();
			if (transmitId == 1)
				trimFromStart++;

			int aimEnable = scanManager.aDecodeGetResultAimIdEnable();
			if (aimEnable == 1 || (decodeResult.decodeValue[0] == (byte) 93 || decodeResult.decodeValue[1] == (byte) 93))
			{
				trimFromStart++;
				if (decodeResult.letter != 0)
					trimFromStart++;
				if (decodeResult.modifier != 0)
					trimFromStart++;

			}

			// strip off terminator if configured
			int terminator = scanManager.aDecodeGetTerminator();
			if (terminator != ScanConst.Terminator.DCD_TERMINATOR_NONE)
			{
				trimFromEnd ++;
				if (terminator == ScanConst.Terminator.DCD_TERMINATOR_TAB_LF) // the only 2-char terminator
					trimFromEnd ++;

			}

			String strBarcode = decodeResult.toString(); // check it before
			if (trimFromStart != 0 || trimFromEnd != 0)
			{
				decodeResult.decodeLength -= (trimFromStart + trimFromEnd);
				decodeResult.decodeValue = Arrays.copyOfRange(decodeResult.decodeValue, trimFromStart, trimFromStart + decodeResult.decodeLength);
			}
			strBarcode = decodeResult.toString(); // check it after

		}
	}

	// scanner message handler
	private ScanResultReceiver scanResultReceiver = new ScanResultReceiver();

	// called during Resume event
	public void openScanner()
	{
		LogHelper.i(TAG, "openScanner: ...");
		//scanManager = new ScanManager();
		scanManager = ScanManager.getInstance();

		LogHelper.i(TAG, "openScanner: done");

	}

	// called during Suspend event
	public void closeScanner()
	{
		LogHelper.i(TAG, "closeScanner: ...");
		if (scanManager != null)
		{
//			scanManager.aDecodeAPIDeinit();
			scanManager = null;
		}
		LogHelper.i(TAG, "closeScanner: done.");
	}

	//endregion


	//region lifecycle

	// If using autoLifecycle, don't call resumeScanner()/pauseScanner() explicity
	public void resumeScanner()
	{
		LogHelper.i(TAG, "resumeScanner: ...");

		initializeScannerSettings();
		scanResultReceiver.register();

		enableScanning(wantScanningEnabled);
		LogHelper.i(TAG, "resumeScanner: done.");
	}

	public void pauseScanner()
	{
		LogHelper.i(TAG, "pauseScanner: ...");
		scanResultReceiver.unregister();

		// disable the trigger
		if (scanManager != null)
		{
			if (scanManager.aDecodeGetTriggerEnable() == DevInfoIndex.ENABLED)
			{
				scanManager.aDecodeSetTriggerEnable(DevInfoIndex.DISABLED);
			}
		}
		LogHelper.i(TAG, "pauseScanner: done.");

	}

	//endregion

	private class ActivityLifeCycleObserver implements LifecycleBinder.LifecycleObserver, ScreenUserStateBinder.ScreenUserObserver
	{

		@Override
		public void onLifecycleStateChanged(Activity activity, ActivityState activityState)
		{
			switch (activityState)
			{
			case PRE_STARTED:
				// open the scanner on pre start so we can be ready in case the app wants to makke calls during its onStart()
				openScanner();
				break;
			case PRE_RESUMED:
				// resume scanning
				resumeScanner();
				break;
			case POST_PAUSED:
				// pause scanninf
				pauseScanner();
				break;
			case POST_STOPPED:
				// close on post stopped so the app can make calls during its onStop()
				closeScanner();
				break;
			}
		}

		@Override
		public void onScreenUserStateChanged(Activity activity, ActivityState activityState)
		{

		}
	}

}
