import { useEffect, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  ActivityIndicator,
  TouchableOpacity,
  StatusBar,
} from "react-native";
import { useRouter } from "expo-router";
import { deviceStore, DeviceConfig } from "../src/store/deviceStore";

export default function HomeScreen() {
  const [config, setConfig] = useState<DeviceConfig | null>(null);
  const router = useRouter();

  useEffect(() => {
    deviceStore.load().then(setConfig);
  }, []);

  async function unpair() {
    await deviceStore.clear();
    router.replace("/pair");
  }

  if (!config) {
    return (
      <View style={styles.container}>
        <ActivityIndicator size="large" color="#fff" />
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <StatusBar barStyle="light-content" backgroundColor="#111" />

      {/* Header */}
      <View style={styles.header}>
        <Text style={styles.museumName}>{config.museum_name}</Text>
        <View style={styles.pairedBadge}>
          <View style={styles.pairedDot} />
          <Text style={styles.pairedText}>Paired</Text>
        </View>
      </View>

      {/* Location */}
      <View style={styles.locationRow}>
        <Text style={styles.locationLabel}>LOCATION</Text>
        <Text style={styles.locationName}>{config.location_name}</Text>
      </View>

      {/* Scan button */}
      <TouchableOpacity style={styles.scanButton} activeOpacity={0.85}>
        <Text style={styles.scanButtonText}>Scan Card</Text>
      </TouchableOpacity>

      {/* Footer */}
      <TouchableOpacity style={styles.unpairButton} onPress={unpair}>
        <Text style={styles.unpairText}>Unpair Device</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#111",
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
    color: "#fff",
    flexShrink: 1,
    marginRight: 12,
  },
  pairedBadge: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "#1e1e1e",
    borderRadius: 20,
    paddingHorizontal: 12,
    paddingVertical: 6,
    gap: 6,
  },
  pairedDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: "#4ade80",
  },
  pairedText: {
    color: "#4ade80",
    fontSize: 13,
    fontWeight: "600",
  },
  locationRow: {
    backgroundColor: "#1e1e1e",
    borderRadius: 16,
    padding: 20,
    marginBottom: 32,
  },
  locationLabel: {
    fontSize: 11,
    fontWeight: "700",
    color: "#666",
    letterSpacing: 1.2,
    marginBottom: 6,
  },
  locationName: {
    fontSize: 20,
    fontWeight: "600",
    color: "#fff",
  },
  scanButton: {
    backgroundColor: "#fff",
    borderRadius: 16,
    paddingVertical: 20,
    alignItems: "center",
    marginBottom: 16,
  },
  scanButtonText: {
    fontSize: 18,
    fontWeight: "700",
    color: "#111",
    letterSpacing: 0.5,
  },
  unpairButton: {
    alignItems: "center",
    paddingVertical: 12,
    marginTop: "auto",
  },
  unpairText: {
    fontSize: 13,
    color: "#444",
  },
});
