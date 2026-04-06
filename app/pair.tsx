import { useRef, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  ActivityIndicator,
  Alert,
  TouchableOpacity,
} from "react-native";
import { CameraView } from "expo-camera";
import { useRouter } from "expo-router";
import { api } from "../src/api/client";
import { deviceStore } from "../src/store/deviceStore";

type PairStatus = "idle" | "pairing" | "error";

export default function PairScreen() {
  const [status, setStatus] = useState<PairStatus>("idle");
  const [errorMessage, setErrorMessage] = useState("");
  const scanningRef = useRef(false);
  const router = useRouter();

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
        const locations = await api.getLocations(
          result.museum_id,
          tokenResp.access_token
        );

        const defaultLocation = locations[0];
        if (!defaultLocation)
          throw {
            body: { full_error_messages: "No locations found for this museum" },
          };

        await deviceStore.save({
          scanner_device_id: result.scanner_device_id,
          museum_id: result.museum_id,
          museum_name: result.museum_name,
          access_token: tokenResp.access_token,
          refresh_token: tokenResp.refresh_token,
          token_expires_at: Date.now() + tokenResp.expires_in * 1000,
          location_id: defaultLocation.id,
          location_name: defaultLocation.name,
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
    // launchScanner resolves when the scanner is dismissed (Android auto-dismisses on scan)
    scanningRef.current = false;
  }

  if (status === "pairing") {
    return (
      <View style={styles.centered}>
        <ActivityIndicator size="large" />
        <Text style={styles.label}>Pairing device...</Text>
      </View>
    );
  }

  if (status === "error") {
    return (
      <View style={styles.centered}>
        <Text style={styles.errorTitle}>Pairing Failed</Text>
        <Text style={styles.errorMessage}>{errorMessage}</Text>
        <TouchableOpacity
          style={styles.button}
          onPress={() => setStatus("idle")}
        >
          <Text style={styles.buttonText}>Try Again</Text>
        </TouchableOpacity>
      </View>
    );
  }

  return (
    <View style={styles.centered}>
      <Text style={styles.title}>Cuseum Scanner</Text>
      <Text style={styles.subtitle}>
        Scan the pairing QR code from the CMS to associate this device with your
        organization.
      </Text>
      <TouchableOpacity style={styles.button} onPress={startScanner}>
        <Text style={styles.buttonText}>Scan Pairing QR Code</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  centered: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: "#fff",
    padding: 32,
    gap: 20,
  },
  title: {
    fontSize: 28,
    fontWeight: "700",
    color: "#111",
  },
  subtitle: {
    fontSize: 15,
    color: "#555",
    textAlign: "center",
    lineHeight: 22,
  },
  button: {
    backgroundColor: "#111",
    paddingHorizontal: 28,
    paddingVertical: 14,
    borderRadius: 10,
  },
  buttonText: {
    color: "#fff",
    fontSize: 16,
    fontWeight: "600",
  },
  label: {
    fontSize: 16,
    color: "#444",
  },
  errorTitle: {
    fontSize: 20,
    fontWeight: "700",
    color: "#c00",
  },
  errorMessage: {
    fontSize: 15,
    color: "#444",
    textAlign: "center",
  },
});
