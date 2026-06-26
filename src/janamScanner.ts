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
  softTrigger(): void;
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

/** Pull the imager trigger programmatically (for an on-screen "Scan" button). */
export function softTrigger() {
  native?.softTrigger();
}

type UseImagerOptions = {
  /** When false, scanning stays disabled even while the screen is focused. */
  enabled?: boolean;
  /** Called with each decoded barcode from the hardware imager. */
  onScan: (event: JanamScanEvent) => void;
};

/**
 * Enables the hardware imager while the screen is focused and routes decoded barcodes to
 * `onScan`. Returns `usingImager` so screens can decide whether to show the camera fallback.
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

  return { usingImager };
}
