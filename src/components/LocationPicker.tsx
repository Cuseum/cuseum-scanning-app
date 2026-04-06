import { useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  Modal,
  Pressable,
  TouchableOpacity,
  FlatList,
  ActivityIndicator,
} from "react-native";
import { deviceStore } from "../store/deviceStore";
import { api } from "../api/client";
import { useTheme, Theme } from "../theme";

type Location = { id: number; name: string; museum_id: number };
type PickerState = "idle" | "loading" | "ready" | "error";

interface Props {
  locationName: string;
  locationId: number;
  museumId: number;
}

export function LocationPicker({ locationName, locationId, museumId }: Props) {
  const theme = useTheme();
  const s = styles(theme);

  const [visible, setVisible] = useState(false);
  const [pickerState, setPickerState] = useState<PickerState>("idle");
  const [locations, setLocations] = useState<Location[]>([]);
  const [error, setError] = useState("");
  const [savingId, setSavingId] = useState<number | null>(null);

  async function open() {
    setVisible(true);
    setPickerState("loading");
    setError("");
    try {
      const data = await api.getLocationsForMuseum(museumId);
      setLocations(data);
      setPickerState("ready");
    } catch (err: any) {
      setError(
        err?.body?.full_error_messages ??
          err?.body?.error ??
          "Could not load locations."
      );
      setPickerState("error");
    }
  }

  async function select(loc: Location) {
    setSavingId(loc.id);
    await deviceStore.update({ location_id: loc.id, location_name: loc.name });
    setSavingId(null);
    setVisible(false);
  }

  function close() {
    if (pickerState === "loading") return;
    setVisible(false);
  }

  return (
    <>
      {/* Tappable location bar */}
      <TouchableOpacity
        style={s.locationBar}
        onPress={open}
        activeOpacity={0.75}
      >
        <View>
          <Text style={s.locationLabel}>CURRENT LOCATION</Text>
          <Text style={s.locationName}>{locationName}</Text>
        </View>
        <Text style={s.chevron}>›</Text>
      </TouchableOpacity>

      {/* Picker modal */}
      <Modal
        visible={visible}
        transparent
        animationType="slide"
        onRequestClose={close}
      >
        <Pressable style={s.backdrop} onPress={close}>
          <Pressable style={s.sheet} onPress={() => {}}>
            <View style={s.handle} />
            <Text style={s.sheetTitle}>Change Location</Text>

            {pickerState === "loading" && (
              <View style={s.center}>
                <ActivityIndicator color={theme.textMuted} />
                <Text style={s.statusText}>Loading locations...</Text>
              </View>
            )}

            {pickerState === "error" && (
              <View style={s.center}>
                <Text style={s.errorText}>{error}</Text>
                <TouchableOpacity style={s.retryBtn} onPress={open}>
                  <Text style={s.retryText}>Retry</Text>
                </TouchableOpacity>
              </View>
            )}

            {pickerState === "ready" && (
              <FlatList
                data={locations}
                keyExtractor={(item) => String(item.id)}
                style={s.list}
                renderItem={({ item }) => {
                  const isCurrent = item.id === locationId;
                  const isSaving = savingId === item.id;
                  return (
                    <TouchableOpacity
                      style={[s.item, isCurrent && s.itemActive]}
                      onPress={() => select(item)}
                      activeOpacity={0.7}
                      disabled={savingId !== null}
                    >
                      <Text style={[s.itemText, isCurrent && s.itemTextActive]}>
                        {item.name}
                      </Text>
                      {isSaving ? (
                        <ActivityIndicator size="small" color={theme.green} />
                      ) : isCurrent ? (
                        <Text style={s.check}>✓</Text>
                      ) : null}
                    </TouchableOpacity>
                  );
                }}
              />
            )}
          </Pressable>
        </Pressable>
      </Modal>
    </>
  );
}

const styles = (t: Theme) =>
  StyleSheet.create({
    locationBar: {
      backgroundColor: t.bgCard,
      borderRadius: 16,
      padding: 16,
      flexDirection: "row",
      alignItems: "center",
      justifyContent: "space-between",
      borderWidth: 1,
      borderColor: t.border,
    },
    locationLabel: {
      fontSize: 11,
      fontWeight: "700",
      color: t.textMuted,
      letterSpacing: 1.2,
      marginBottom: 4,
    },
    locationName: {
      fontSize: 15,
      fontWeight: "600",
      color: t.textPrimary,
    },
    chevron: {
      fontSize: 24,
      color: t.textMuted,
      lineHeight: 28,
    },
    backdrop: {
      flex: 1,
      backgroundColor: "rgba(0,0,0,0.6)",
      justifyContent: "flex-end",
    },
    sheet: {
      backgroundColor: t.bgModal,
      borderTopLeftRadius: 20,
      borderTopRightRadius: 20,
      borderTopWidth: 1,
      borderColor: t.border,
      paddingTop: 12,
      paddingBottom: 40,
      maxHeight: "70%",
    },
    handle: {
      width: 36,
      height: 4,
      borderRadius: 2,
      backgroundColor: t.border,
      alignSelf: "center",
      marginBottom: 16,
    },
    sheetTitle: {
      fontSize: 17,
      fontWeight: "700",
      color: t.textPrimary,
      textAlign: "center",
      marginBottom: 8,
      paddingHorizontal: 24,
    },
    center: {
      alignItems: "center",
      justifyContent: "center",
      paddingVertical: 40,
      gap: 12,
    },
    statusText: {
      color: t.textMuted,
      fontSize: 14,
      marginTop: 8,
    },
    errorText: {
      color: "#f87171",
      fontSize: 14,
      textAlign: "center",
      paddingHorizontal: 24,
    },
    retryBtn: {
      paddingHorizontal: 24,
      paddingVertical: 10,
      borderRadius: 10,
      borderWidth: 1,
      borderColor: t.border,
    },
    retryText: {
      color: t.textMuted,
      fontSize: 14,
      fontWeight: "600",
    },
    list: {
      marginTop: 4,
    },
    item: {
      flexDirection: "row",
      alignItems: "center",
      justifyContent: "space-between",
      paddingHorizontal: 24,
      paddingVertical: 18,
      borderBottomWidth: 1,
      borderBottomColor: t.separator,
    },
    itemActive: {
      backgroundColor: t.greenBgDeep,
    },
    itemText: {
      fontSize: 16,
      color: t.textSecondary,
      flex: 1,
    },
    itemTextActive: {
      color: t.green,
      fontWeight: "600",
    },
    check: {
      fontSize: 18,
      color: t.green,
      fontWeight: "700",
    },
  });
