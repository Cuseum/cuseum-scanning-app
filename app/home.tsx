import { useRef } from "react";
import {
  View,
  Text,
  StyleSheet,
  ActivityIndicator,
  TouchableOpacity,
  StatusBar,
  Alert,
} from "react-native";
import { useRouter } from "expo-router";
import { CameraView } from "expo-camera";
import { deviceStore, useDeviceConfig } from "../src/store/deviceStore";
import { api } from "../src/api/client";
import { LocationPicker } from "../src/components/LocationPicker";
import { useTheme, Theme } from "../src/theme";

export default function HomeScreen() {
  const config = useDeviceConfig();
  const scanningRef = useRef(false);
  const router = useRouter();
  const theme = useTheme();
  const s = styles(theme);

  async function unpair() {
    const config = await deviceStore.load();
    if (config?.scanner_device_id) {
      try {
        await api.unpairDevice(config.scanner_device_id);
      } catch {
        // non-critical — clear locally regardless
      }
    }
    await deviceStore.clear();
    router.replace("/pair");
  }

  async function scanCard() {
    if (!config?.location_id) {
      Alert.alert(
        "No Location Selected",
        "Please select a location before scanning.",
        [{ text: "OK" }]
      );
      return;
    }
    if (scanningRef.current) return;
    scanningRef.current = true;

    const subscription = CameraView.onModernBarcodeScanned(({ data }) => {
      subscription.remove();
      CameraView.dismissScanner();
      scanningRef.current = false;
      router.push({ pathname: "/scan-result", params: { barcode: data } });
    });

    await CameraView.launchScanner({
      barcodeTypes: ["qr", "code128", "aztec", "pdf417"],
    });
    scanningRef.current = false;
  }

  if (!config) {
    return (
      <View style={s.container}>
        <ActivityIndicator size="large" color={theme.textPrimary} />
      </View>
    );
  }

  return (
    <View style={s.container}>
      <StatusBar barStyle={theme.statusBar} backgroundColor={theme.bg} />

      {/* Header */}
      <View style={s.header}>
        <Text style={s.museumName}>{config.museum_name}</Text>
        <View style={s.pairedBadge}>
          <View style={s.pairedDot} />
          <Text style={s.pairedText}>Paired</Text>
        </View>
      </View>

      {/* Location picker */}
      <View style={s.locationWrapper}>
        <LocationPicker
          locationName={config.location_name}
          locationId={config.location_id}
          museumId={config.museum_id}
        />
      </View>

      {/* Scan button */}
      <TouchableOpacity
        style={s.scanButton}
        activeOpacity={0.85}
        onPress={scanCard}
      >
        <Text style={s.scanButtonText}>Scan Card</Text>
      </TouchableOpacity>

      {/* Search button */}
      <TouchableOpacity
        style={s.searchButton}
        activeOpacity={0.85}
        onPress={() => {
          if (!config?.location_id) {
            Alert.alert(
              "No Location Selected",
              "Please select a location before searching.",
              [{ text: "OK" }]
            );
            return;
          }
          router.push("/search");
        }}
      >
        <Text style={s.searchButtonText}>Search Member</Text>
      </TouchableOpacity>

      {/* Footer */}
      <TouchableOpacity style={s.unpairButton} onPress={unpair}>
        <Text style={s.unpairText}>Unpair Device</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = (t: Theme) =>
  StyleSheet.create({
    container: {
      flex: 1,
      backgroundColor: t.bg,
      paddingHorizontal: 28,
      paddingTop: 64,
      paddingBottom: 40,
    },
    header: {
      flexDirection: "row",
      alignItems: "center",
      justifyContent: "space-between",
      marginBottom: 48,
    },
    museumName: {
      fontSize: 22,
      fontWeight: "700",
      color: t.textPrimary,
      flexShrink: 1,
      marginRight: 12,
    },
    pairedBadge: {
      flexDirection: "row",
      alignItems: "center",
      backgroundColor: t.bgCard,
      borderRadius: 20,
      paddingHorizontal: 12,
      paddingVertical: 6,
      gap: 6,
    },
    pairedDot: {
      width: 8,
      height: 8,
      borderRadius: 4,
      backgroundColor: t.green,
    },
    pairedText: {
      color: t.green,
      fontSize: 13,
      fontWeight: "600",
    },
    locationWrapper: {
      marginBottom: 32,
    },
    scanButton: {
      backgroundColor: t.scanBtnBg,
      borderRadius: 16,
      paddingVertical: 20,
      alignItems: "center",
      marginBottom: 16,
    },
    scanButtonText: {
      fontSize: 18,
      fontWeight: "700",
      color: t.scanBtnText,
      letterSpacing: 0.5,
    },
    searchButton: {
      backgroundColor: t.scanBtnBg,
      borderRadius: 16,
      paddingVertical: 18,
      alignItems: "center",
      marginBottom: 16,
    },
    searchButtonText: {
      fontSize: 16,
      fontWeight: "600",
      color: t.scanBtnText,
      letterSpacing: 0.5,
    },
    unpairButton: {
      alignItems: "center",
      paddingVertical: 12,
      marginTop: "auto",
    },
    unpairText: {
      fontSize: 13,
      color: t.textMuted,
    },
  });
