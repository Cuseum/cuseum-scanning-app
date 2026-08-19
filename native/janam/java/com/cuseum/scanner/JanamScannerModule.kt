package com.cuseum.scanner

import android.app.Activity
import android.os.Build
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.UiThreadUtil
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.janam.device.sdk.ScanManager
import com.janam.janamdevicesdk.JanamScanner

/**
 * Bridges the Janam hardware imager (XT40 and other supported Janam terminals) to React Native.
 *
 * On a Janam device the physical scan trigger fires the imager directly; decoded barcodes are
 * delivered through [JanamScanner]'s broadcast receiver and re-emitted to JS as `JanamScan`
 * events. On non-Janam hardware (a normal phone, the iOS simulator, etc.) [isJanamDevice]
 * resolves false and the JS layer falls back to expo-camera.
 *
 * Scanning is enabled per-screen via [setEnabled]; [softTrigger] lets an on-screen button pull
 * the trigger programmatically as an alternative to the hardware key.
 */
class JanamScannerModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext), LifecycleEventListener {

  private var scanner: JanamScanner? = null
  private var wantEnabled = false
  private var initFailed = false
  // Cached hardware detection (null = not probed yet).
  private var janamAvailable: Boolean? = null
  // Tracks whether the SDK broadcast receiver is currently registered, so we never
  // double-register it (which would deliver each scan twice).
  private var resumed = false

  init {
    reactContext.addLifecycleEventListener(this)
  }

  override fun getName(): String = NAME

  // Fast path: most Janam terminals report "Janam" in a Build field.
  private fun isJanamHardware(): Boolean {
    val fields =
        listOf(Build.MANUFACTURER, Build.BRAND, Build.MODEL, Build.DEVICE, Build.PRODUCT)
    return fields.any { it?.contains("janam", ignoreCase = true) == true }
  }

  // Authoritative check: can we actually talk to the device's scanner service? This works even
  // if the Build strings don't say "janam".
  //
  // IMPORTANT: every ScanManager call catches its own failures and returns a sentinel (-1 for
  // ints, "Unknown" for strings) instead of throwing — on a phone with no ScannerService the
  // SDK throws RemoteException internally and swallows it. So the probe has to inspect the
  // returned values; a try/catch alone reports every Android device as a Janam device.
  private fun hasJanamScanner(): Boolean {
    return try {
      val sm = ScanManager.getInstance() ?: return false
      val decodeEnable = sm.aDecodeGetDecodeEnable() // 0/1 on a real scanner, -1 on failure
      val apiVersion = sm.aDecodeGetAPIVersion() // version string, "Unknown" on failure
      val moduleName = sm.aDecodeGetModuleName() // imager name, "Unknown" on failure
      val ok = decodeEnable >= 0 || isRealValue(apiVersion) || isRealValue(moduleName)
      Log.i(
          TAG,
          "hasJanamScanner: decodeEnable=$decodeEnable apiVersion=$apiVersion " +
              "moduleName=$moduleName -> $ok"
      )
      ok
    } catch (t: Throwable) {
      Log.i(TAG, "hasJanamScanner: probe failed: ${t.message}")
      false
    }
  }

  private fun isRealValue(value: String?): Boolean =
      !value.isNullOrBlank() && !value.equals("Unknown", ignoreCase = true)

  /** Detection result, computed once and cached (null until probed). Must run on the UI thread. */
  private fun janamAvailable(): Boolean {
    janamAvailable?.let { return it }
    val byBuild = isJanamHardware()
    val result = byBuild || hasJanamScanner()
    Log.i(TAG, "janamAvailable: byBuild=$byBuild -> result=$result")
    janamAvailable = result
    return result
  }

  @ReactMethod
  fun isJanamDevice(promise: Promise) {
    Log.i(
        TAG,
        "Build: manufacturer=${Build.MANUFACTURER} brand=${Build.BRAND} " +
            "model=${Build.MODEL} device=${Build.DEVICE} product=${Build.PRODUCT}"
    )
    UiThreadUtil.runOnUiThread { promise.resolve(janamAvailable()) }
  }

  /** Lazily create (but do not yet open/resume) the scanner against the current activity. */
  private fun ensureScanner(): JanamScanner? {
    if (scanner != null) return scanner
    if (initFailed) return null
    // Gate on detection: on non-Janam hardware every SDK call silently no-ops, so creating a
    // scanner here would leave the app waiting on an imager that can never fire instead of
    // falling back to expo-camera.
    if (!janamAvailable()) {
      Log.i(TAG, "ensureScanner: not a Janam device — camera fallback")
      initFailed = true
      return null
    }
    val activity: Activity = getCurrentActivity() ?: return null
    try {
      val instance = JanamScanner.createInstance(activity, false /* autoLifeCycle */)
      instance.setScanReceiver { decodeResult -> onDecode(decodeResult) }
      // Default feedback: let the device beep, no extra vibrate (the app provides its own
      // haptics/sound on the result screen).
      instance.setScanBeepEnabled(true)
      instance.setScanVibrateEnabled(false)
      // Read barcodes shown on screens reliably: the pairing QR (on the CMS monitor) and
      // members' digital wallet passes (Apple/Google Wallet on a phone).
      instance.setPhoneMode(true)
      scanner = instance
      Log.i(TAG, "JanamScanner created")
    } catch (t: Throwable) {
      Log.w(TAG, "JanamScanner creation failed: ${t.message}")
      initFailed = true
      scanner = null
    }
    return scanner
  }

  /**
   * Open + resume the scanner exactly once per resume cycle, then apply the desired enabled
   * state. RN's JS starts while the activity is already resumed and the SDK's own lifecycle
   * binder only reacts to future transitions, so we bootstrap resume here and mirror RN host
   * lifecycle events below. Guarded by [resumed] to avoid double-registering the receiver.
   */
  private fun resumeAndApply() {
    val s = ensureScanner() ?: return
    if (!resumed) {
      s.openScanner()
      s.resumeScanner()
      // Force broadcast-only output so scans aren't copied to the clipboard (COPYPASTE) or
      // typed as keystrokes (KBDMSG). Log the before/after so we can confirm on-device.
      val before = s.resultType
      s.useBroadcastResultOnly()
      Log.i(TAG, "resultType: before=$before after=${s.resultType} (0=USERMSG,1=KBDMSG,2=COPYPASTE,3=EVENT)")
      resumed = true
    }
    Log.i(TAG, "enableScanning($wantEnabled) — hardware trigger ${if (wantEnabled) "armed" else "disarmed"}")
    s.enableScanning(wantEnabled)
  }

  private fun onDecode(decodeResult: JanamScanner.DecodeResult) {
    val data = decodeResult.decodeString ?: return
    Log.i(TAG, "onDecode: '${data}' (${decodeResult.symName})")
    if (data.isEmpty()) return
    val map: WritableMap = Arguments.createMap()
    map.putString("data", data)
    map.putString("symbology", decodeResult.symName ?: "")
    sendEvent(SCAN_EVENT, map)
  }

  private fun sendEvent(name: String, body: WritableMap) {
    if (!reactApplicationContext.hasActiveReactInstance()) return
    reactApplicationContext
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
        .emit(name, body)
  }

  /** Enable/disable scanning (the hardware trigger) while a scan-capable screen is focused. */
  @ReactMethod
  fun setEnabled(enabled: Boolean) {
    wantEnabled = enabled
    UiThreadUtil.runOnUiThread { resumeAndApply() }
  }

  /**
   * Programmatically pull the trigger (soft scan) — used by on-screen scan buttons. Resolves
   * true when the imager was actually armed and false when there is no imager to arm, so the
   * JS layer can fall back to the camera instead of silently doing nothing.
   */
  @ReactMethod
  fun softTrigger(promise: Promise) {
    UiThreadUtil.runOnUiThread {
      val s = ensureScanner()
      if (s == null) {
        promise.resolve(false)
        return@runOnUiThread
      }
      resumeAndApply()
      s.enableScanning(true)
      s.triggerScanning(true)
      promise.resolve(true)
    }
  }

  // Required so JS NativeEventEmitter doesn't warn; events are dispatched via RCTDeviceEventEmitter.
  @ReactMethod
  fun addListener(eventName: String) {}

  @ReactMethod
  fun removeListeners(count: Int) {}

  // ---- RN host lifecycle -> Janam scanner lifecycle ----

  override fun onHostResume() {
    UiThreadUtil.runOnUiThread { resumeAndApply() }
  }

  override fun onHostPause() {
    UiThreadUtil.runOnUiThread {
      if (resumed) {
        scanner?.pauseScanner()
        resumed = false
      }
    }
  }

  override fun onHostDestroy() {
    UiThreadUtil.runOnUiThread {
      if (resumed) {
        scanner?.pauseScanner()
        resumed = false
      }
      scanner?.closeScanner()
      scanner = null
    }
  }

  companion object {
    const val NAME = "JanamScanner"
    const val SCAN_EVENT = "JanamScan"
    const val TAG = "JanamScanner"
  }
}
