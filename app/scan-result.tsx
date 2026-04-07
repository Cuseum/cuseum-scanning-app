import { useEffect, useRef, useState } from "react";
import { useLocalSearchParams, useRouter } from "expo-router";
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  StatusBar,
  ActivityIndicator,
} from "react-native";
import { createAudioPlayer, setAudioModeAsync } from "expo-audio";
import * as Haptics from "expo-haptics";
import { CameraView } from "expo-camera";
import { deviceStore } from "../src/store/deviceStore";
import { api } from "../src/api/client";
import { SOUNDS } from "../src/sounds";
import { useTheme } from "../src/theme";
import type { ValidationResult } from "../src/types";

type ScreenState =
  | "loading"
  | "valid"
  | "expired"
  | "invalid"
  | "error"
  | "entry_allowed";

async function playSound(uri: string) {
  try {
    await setAudioModeAsync({ playsInSilentMode: true });
    const player = createAudioPlayer({ uri });
    player.play();
  } catch {
    // sound failure is non-critical
  }
}

async function feedbackValid() {
  await Promise.all([
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success),
    playSound(SOUNDS.success),
  ]);
}

async function feedbackExpired() {
  await Promise.all([
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning),
    playSound(SOUNDS.expired),
  ]);
}

async function feedbackInvalid() {
  await Promise.all([
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error),
    playSound(SOUNDS.invalid),
  ]);
}

export default function ScanResultScreen() {
  const { barcode } = useLocalSearchParams<{ barcode: string }>();
  const router = useRouter();
  const theme = useTheme();

  const [state, setState] = useState<ScreenState>("loading");
  const [result, setResult] = useState<ValidationResult | null>(null);
  const [errorMessage, setErrorMessage] = useState("");
  const [locationName, setLocationName] = useState("");
  const feedbackFired = useRef(false);
  const scanningRef = useRef(false);
  const configRef = useRef<
    import("../src/store/deviceStore").DeviceConfig | null
  >(null);

  useEffect(() => {
    validate();
  }, []);

  async function validate() {
    try {
      const config = await deviceStore.load();
      if (!config) {
        router.replace("/pair");
        return;
      }
      configRef.current = config;
      setLocationName(config.location_name ?? "");

      const data = (await api.validateCard(
        barcode,
        config.scanner_device_id
      )) as ValidationResult;
      setResult(data);

      if (data.reason === "active") {
        setState("valid");
        if (!feedbackFired.current) {
          feedbackFired.current = true;
          feedbackValid();
        }
        // Story 13: record attendance automatically on valid scan
        api
          .recordAttendance({
            external_user_id: data.external_user_id,
            location_id: config.location_id,
            scanner_device_id: config.scanner_device_id,
          })
          .catch(() => {
            // non-critical — don't block the UI
          });
      } else if (data.reason === "expired") {
        setState("expired");
        if (!feedbackFired.current) {
          feedbackFired.current = true;
          feedbackExpired();
        }
      } else {
        setState("invalid");
        if (!feedbackFired.current) {
          feedbackFired.current = true;
          feedbackInvalid();
        }
      }
    } catch (err: any) {
      const msg =
        err?.body?.full_error_messages ??
        err?.body?.error ??
        "Validation failed. Please try again.";
      setErrorMessage(msg);
      setState("error");
      if (!feedbackFired.current) {
        feedbackFired.current = true;
        feedbackInvalid();
      }
    }
  }

  async function scanAnother() {
    if (scanningRef.current) return;
    scanningRef.current = true;

    const subscription = CameraView.onModernBarcodeScanned(({ data }) => {
      subscription.remove();
      CameraView.dismissScanner();
      scanningRef.current = false;
      router.replace({ pathname: "/scan-result", params: { barcode: data } });
    });

    await CameraView.launchScanner({
      barcodeTypes: ["qr", "code128", "aztec", "pdf417"],
    });
    scanningRef.current = false;
  }

  function allowEntryAnyway() {
    const config = configRef.current;
    if (!config || !result) return;
    api
      .recordAttendance({
        external_user_id: result.external_user_id,
        location_id: config.location_id,
        scanner_device_id: config.scanner_device_id,
      })
      .catch(() => {
        // non-critical
      });
    setState("entry_allowed");
  }

  if (state === "loading") {
    return (
      <View style={[styles.container, { backgroundColor: theme.bg }]}>
        <StatusBar barStyle={theme.statusBar} />
        <ActivityIndicator size="large" color={theme.textPrimary} />
        <Text style={[styles.loadingText, { color: theme.textMuted }]}>
          Validating card...
        </Text>
      </View>
    );
  }

  if (state === "valid" && result) {
    return (
      <View style={[styles.container, styles.validContainer]}>
        <StatusBar barStyle="light-content" />
        {locationName ? (
          <Text style={styles.locationLabel}>📍 {locationName}</Text>
        ) : null}
        <Text style={styles.icon}>✓</Text>
        <Text style={styles.statusTitle}>Valid Card</Text>

        <View style={styles.memberCard}>
          <Text style={styles.memberName}>{result.member.name}</Text>
          {result.member.external_id && (
            <Text style={styles.memberDetail}>
              ID: {result.member.external_id}
            </Text>
          )}
          {result.member.membership_level && (
            <Text style={styles.memberDetail}>
              {result.member.membership_level}
            </Text>
          )}
          {result.member.expires_at &&
            result.member.expires_at !== "Infinity" && (
              <Text style={styles.memberDetail}>
                Expires{" "}
                {new Date(result.member.expires_at).toLocaleDateString()}
              </Text>
            )}
        </View>

        <TouchableOpacity style={styles.button} onPress={scanAnother}>
          <Text style={styles.buttonText}>Scan Another</Text>
        </TouchableOpacity>
      </View>
    );
  }

  if (state === "expired" && result) {
    return (
      <View style={[styles.container, styles.expiredContainer]}>
        <StatusBar barStyle="light-content" />
        {locationName ? (
          <Text style={styles.locationLabel}>📍 {locationName}</Text>
        ) : null}
        <Text style={styles.icon}>⚠</Text>
        <Text style={styles.statusTitle}>Card Expired</Text>

        <View style={styles.memberCard}>
          <Text style={styles.memberName}>{result.member.name}</Text>
          {result.member.external_id && (
            <Text style={styles.memberDetail}>
              ID: {result.member.external_id}
            </Text>
          )}
          {result.member.expires_at &&
            result.member.expires_at !== "Infinity" && (
              <Text style={styles.memberDetail}>
                Expired{" "}
                {new Date(result.member.expires_at).toLocaleDateString()}
              </Text>
            )}
        </View>

        <TouchableOpacity style={styles.button} onPress={scanAnother}>
          <Text style={styles.buttonText}>Scan Another</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.allowEntryButton}
          onPress={allowEntryAnyway}
        >
          <Text style={styles.allowEntryText}>Allow Entry Anyway</Text>
        </TouchableOpacity>
      </View>
    );
  }

  if (state === "entry_allowed" && result) {
    return (
      <View style={[styles.container, styles.entryAllowedContainer]}>
        <StatusBar barStyle="light-content" />
        {locationName ? (
          <Text style={styles.locationLabel}>📍 {locationName}</Text>
        ) : null}
        <Text style={styles.icon}>✓</Text>
        <Text style={styles.statusTitle}>Entry Allowed</Text>
        <Text style={styles.statusSubtitle}>
          Attendance recorded for {result.member.name}
        </Text>
        <TouchableOpacity style={styles.button} onPress={scanAnother}>
          <Text style={styles.buttonText}>Scan Another</Text>
        </TouchableOpacity>
      </View>
    );
  }

  if (state === "invalid") {
    return (
      <View style={[styles.container, styles.invalidContainer]}>
        <StatusBar barStyle="light-content" />
        {locationName ? (
          <Text style={styles.locationLabel}>📍 {locationName}</Text>
        ) : null}
        <Text style={styles.icon}>✕</Text>
        <Text style={styles.statusTitle}>Invalid Card</Text>
        <Text style={styles.statusSubtitle}>This card could not be found.</Text>
        <TouchableOpacity style={styles.button} onPress={scanAnother}>
          <Text style={styles.buttonText}>Scan Another</Text>
        </TouchableOpacity>
      </View>
    );
  }

  // error
  return (
    <View style={[styles.container, styles.invalidContainer]}>
      <StatusBar barStyle="light-content" />
      <Text style={styles.icon}>✕</Text>
      <Text style={styles.statusTitle}>Error</Text>
      <Text style={styles.statusSubtitle}>{errorMessage}</Text>
      <TouchableOpacity
        style={styles.button}
        onPress={() => router.replace("/home")}
      >
        <Text style={styles.buttonText}>Go Back</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    padding: 32,
    gap: 16,
  },
  validContainer: { backgroundColor: "#14532d" },
  expiredContainer: { backgroundColor: "#78350f" },
  entryAllowedContainer: { backgroundColor: "#1e3a5f" },
  invalidContainer: { backgroundColor: "#7f1d1d" },
  loadingText: {
    fontSize: 16,
    marginTop: 12,
  },
  icon: {
    fontSize: 64,
    color: "#fff",
  },
  statusTitle: {
    fontSize: 28,
    fontWeight: "700",
    color: "#fff",
  },
  statusSubtitle: {
    fontSize: 15,
    color: "rgba(255,255,255,0.7)",
    textAlign: "center",
  },
  memberCard: {
    backgroundColor: "rgba(0,0,0,0.2)",
    borderRadius: 16,
    padding: 20,
    width: "100%",
    gap: 6,
    marginTop: 8,
  },
  memberName: {
    fontSize: 20,
    fontWeight: "700",
    color: "#fff",
  },
  memberDetail: {
    fontSize: 14,
    color: "rgba(255,255,255,0.7)",
  },
  locationLabel: {
    position: "absolute",
    top: 56,
    fontSize: 13,
    color: "rgba(255,255,255,0.6)",
    fontWeight: "500",
  },
  button: {
    marginTop: 16,
    backgroundColor: "rgba(255,255,255,0.15)",
    borderRadius: 14,
    paddingVertical: 16,
    paddingHorizontal: 40,
    borderWidth: 1,
    borderColor: "rgba(255,255,255,0.3)",
  },
  buttonText: {
    fontSize: 16,
    fontWeight: "700",
    color: "#fff",
  },
  allowEntryButton: {
    paddingVertical: 12,
    paddingHorizontal: 24,
  },
  allowEntryText: {
    fontSize: 14,
    fontWeight: "600",
    color: "rgba(255,255,255,0.5)",
    textDecorationLine: "underline",
  },
});
