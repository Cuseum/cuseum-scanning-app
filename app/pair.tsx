import { useRef, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  ActivityIndicator,
  Alert,
  TouchableOpacity,
  StatusBar,
} from "react-native";
import { CameraView } from "expo-camera";
import { useRouter } from "expo-router";
import { api } from "../src/api/client";
import { deviceStore } from "../src/store/deviceStore";
import { useTheme, Theme } from "../src/theme";

type PairStatus = "idle" | "pairing" | "error";

export default function PairScreen() {
  const [status, setStatus] = useState<PairStatus>("idle");
  const [errorMessage, setErrorMessage] = useState("");
  const scanningRef = useRef(false);
  const router = useRouter();
  const theme = useTheme();
  const s = styles(theme);

  async function startScanner() {
    if (scanningRef.current) return;
    scanningRef.current = true;

    const subscription = CameraView.onModernBarcodeScanned(async ({ data }) => {
      subscription.remove();
      CameraView.dismissScanner();

      let scanner_device_id: number;
      try {
        const payload = JSON.parse(data);
        scanner_device_id = payload.scanner_device_id;
        if (!scanner_device_id) throw new Error("Missing scanner_device_id");
      } catch {
        scanningRef.current = false;
        Alert.alert(
          "Invalid QR Code",
          "This QR code is not a valid pairing code.",
          [{ text: "OK" }]
        );
        return;
      }

      setStatus("pairing");

      try {
        const tokenResp = await api.getToken();
        const result = await api.pairDevice(
          scanner_device_id,
          tokenResp.access_token
        );

        await deviceStore.save({
          scanner_device_id: result.scanner_device_id,
          museum_id: result.museum_id,
          museum_name: result.museum_name,
          access_token: tokenResp.access_token,
          refresh_token: tokenResp.refresh_token,
          token_expires_at: Date.now() + tokenResp.expires_in * 1000,
          location_id: null,
          location_name: null,
        });

        router.replace("/home");
      } catch (err: any) {
        const message =
          err?.body?.full_error_messages ??
          err?.body?.error_description ??
          "Pairing failed. Please try again.";
        setErrorMessage(message);
        setStatus("error");
        scanningRef.current = false;
      }
    });

    await CameraView.launchScanner({
      barcodeTypes: ["qr", "code128", "ean13", "ean8"],
    });
    scanningRef.current = false;
  }

  if (status === "pairing") {
    return (
      <View style={s.centered}>
        <StatusBar barStyle={theme.statusBar} backgroundColor={theme.bg} />
        <ActivityIndicator size="large" color={theme.textPrimary} />
        <Text style={s.label}>Pairing device...</Text>
      </View>
    );
  }

  if (status === "error") {
    return (
      <View style={s.centered}>
        <StatusBar barStyle={theme.statusBar} backgroundColor={theme.bg} />
        <Text style={s.errorTitle}>Pairing Failed</Text>
        <Text style={s.errorMessage}>{errorMessage}</Text>
        <TouchableOpacity style={s.button} onPress={() => setStatus("idle")}>
          <Text style={s.buttonText}>Try Again</Text>
        </TouchableOpacity>
      </View>
    );
  }

  return (
    <View style={s.centered}>
      <StatusBar barStyle={theme.statusBar} backgroundColor={theme.bg} />
      <Text style={s.title}>Cuseum Scanner</Text>
      <Text style={s.subtitle}>
        Scan the pairing QR code from the CMS to associate this device with your
        organization.
      </Text>
      <TouchableOpacity style={s.button} onPress={startScanner}>
        <Text style={s.buttonText}>Scan Pairing QR Code</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = (t: Theme) =>
  StyleSheet.create({
    centered: {
      flex: 1,
      alignItems: "center",
      justifyContent: "center",
      backgroundColor: t.bg,
      padding: 32,
      gap: 20,
    },
    title: {
      fontSize: 28,
      fontWeight: "700",
      color: t.textPrimary,
    },
    subtitle: {
      fontSize: 15,
      color: t.textMuted,
      textAlign: "center",
      lineHeight: 22,
    },
    button: {
      backgroundColor: t.scanBtnBg,
      paddingHorizontal: 28,
      paddingVertical: 14,
      borderRadius: 10,
    },
    buttonText: {
      color: t.scanBtnText,
      fontSize: 16,
      fontWeight: "600",
    },
    label: {
      fontSize: 16,
      color: t.textMuted,
    },
    errorTitle: {
      fontSize: 20,
      fontWeight: "700",
      color: "#dc2626",
    },
    errorMessage: {
      fontSize: 15,
      color: t.textMuted,
      textAlign: "center",
    },
  });
