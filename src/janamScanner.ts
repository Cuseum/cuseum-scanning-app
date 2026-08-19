import { useCallback, useEffect, useRef, useState } from "react";
import { NativeEventEmitter, NativeModules, Platform } from "react-native";
import { useFocusEffect } from "@react-navigation/native";

/**
 * Bridge to the native Janam hardware imager (XT40 and other supported Janam terminals).
 *
 * On a Janam device the physical scan trigger drives the imager and decoded barcodes arrive as
 * `JanamScan` events. On any other device (a normal phone, the iOS simulator) the native module
 * is absent or reports `isJanamDevice() === false`, and callers fall back to expo-camera.
 */

type NativeJanamScanner = {
  isJanamDevice(): Promise<boolean>;
  setEnabled(enabled: boolean): void;
  softTrigger(): Promise<boolean>;
  addListener(eventName: string): void;
  removeListeners(count: number): void;
};

export type JanamScanEvent = {
  data: string;
  symbology: string;
};

const native: NativeJanamScanner | undefined =
  Platform.OS === "android"
    ? (NativeModules.JanamScanner as NativeJanamScanner | undefined)
    : undefined;

const emitter = native ? new NativeEventEmitter(NativeModules.JanamScanner) : null;

export async function isJanamDevice(): Promise<boolean> {
  if (!native) return false;
  try {
    return await native.isJanamDevice();
  } catch {
    return false;
  }
}

function setEnabled(enabled: boolean) {
  native?.setEnabled(enabled);
}

/**
 * Pull the imager trigger programmatically (for an on-screen "Scan" button). Resolves false when
 * there is no hardware imager to trigger, so callers can fall back to the camera.
 */
export async function softTrigger(): Promise<boolean> {
  if (!native) return false;
  try {
    return await native.softTrigger();
  } catch {
    return false;
  }
}

type UseImagerOptions = {
  /** When false, scanning stays disabled even while the screen is focused. */
  enabled?: boolean;
  /** Called with each decoded barcode from the hardware imager. */
  onScan: (event: JanamScanEvent) => void;
};

/**
 * Enables the hardware imager while the screen is focused and routes decoded barcodes to
 * `onScan`. Returns `usingImager` so screens can decide whether to show the camera fallback,
 * plus `scanWithImager()` for on-screen scan buttons.
 *
 * `usingImager` is `null` until device detection resolves, then `true`/`false`.
 */
export function useImagerScanner({ enabled = true, onScan }: UseImagerOptions) {
  const [usingImager, setUsingImager] = useState<boolean | null>(null);
  const onScanRef = useRef(onScan);
  onScanRef.current = onScan;

  useEffect(() => {
    let mounted = true;
    isJanamDevice().then((v) => {
      if (mounted) setUsingImager(v);
    });
    return () => {
      mounted = false;
    };
  }, []);

  useFocusEffect(
    useCallback(() => {
      if (!usingImager || !enabled || !emitter) return;

      const sub = emitter.addListener("JanamScan", (event: JanamScanEvent) => {
        onScanRef.current(event);
      });
      setEnabled(true);

      return () => {
        setEnabled(false);
        sub.remove();
      };
    }, [usingImager, enabled])
  );

  /**
   * Try to scan with the hardware imager. Returns true when the imager was armed (the caller is
   * done) and false when this device has no imager and the caller should open the camera. If the
   * trigger turns out not to work, we stop claiming this is an imager device so every later scan
   * — and the UI hint — goes back to the camera.
   */
  const scanWithImager = useCallback(async () => {
    if (!usingImager) return false;
    const triggered = await softTrigger();
    if (!triggered) setUsingImager(false);
    return triggered;
  }, [usingImager]);

  return { usingImager, scanWithImager };
}
