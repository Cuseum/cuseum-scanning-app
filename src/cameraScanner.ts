import { CameraView, type BarcodeType } from "expo-camera";

/**
 * Wrapper around `CameraView.launchScanner()` (the Google code scanner on Android, VisionKit on
 * iOS) that reports how the scanner closed instead of throwing.
 *
 * The raw call **rejects** in two very different situations: the user dismissed the scanner
 * without scanning (`ERR_BARCODE_SCANNING_CANCELLED`, from the SDK's cancel listener), and the
 * scanner could not launch at all (no Google Play Services / ML Kit). Both leave the caller's
 * "scan in progress" latch and `onModernBarcodeScanned` listener dangling, which silently blocks
 * every later scan attempt — so callers must handle them, and an easy-to-miss `await` that
 * throws is the wrong shape for that.
 */
export type ScannerOutcome =
  /** The scanner closed with a barcode — it was delivered to `onModernBarcodeScanned`. */
  | "scanned"
  /** The user dismissed the scanner. Nothing was scanned; not an error. */
  | "cancelled"
  /** The scanner never opened (Play Services / ML Kit unavailable, or an SDK failure). */
  | "failed";

const CANCELLED_CODE = "ERR_BARCODE_SCANNING_CANCELLED";

export async function launchCameraScanner(
  barcodeTypes: BarcodeType[]
): Promise<ScannerOutcome> {
  try {
    await CameraView.launchScanner({ barcodeTypes });
    return "scanned";
  } catch (err: any) {
    if (err?.code === CANCELLED_CODE) return "cancelled";
    console.warn("launchCameraScanner failed:", err?.code, err?.message);
    return "failed";
  }
}
