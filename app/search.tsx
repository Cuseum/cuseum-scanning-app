import { useEffect, useRef, useState } from "react";
import {
  View,
  Text,
  TextInput,
  StyleSheet,
  FlatList,
  TouchableOpacity,
  StatusBar,
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  Modal,
  Pressable,
} from "react-native";
import { useRouter } from "expo-router";
import { useDeviceConfig } from "../src/store/deviceStore";
import { api } from "../src/api/client";
import type { Member } from "../src/types";
import { LocationPicker } from "../src/components/LocationPicker";
import { useTheme, Theme } from "../src/theme";

type SearchState = "idle" | "loading" | "done" | "error";
type ModalState = "confirm" | "recording" | "success" | "error";

export default function SearchScreen() {
  const router = useRouter();
  const config = useDeviceConfig();
  const theme = useTheme();
  const s = styles(theme);

  const [query, setQuery] = useState("");
  const [state, setState] = useState<SearchState>("idle");
  const [results, setResults] = useState<Member[]>([]);
  const [errorMessage, setErrorMessage] = useState("");

  const [selectedMember, setSelectedMember] = useState<Member | null>(null);
  const [modalState, setModalState] = useState<ModalState>("confirm");
  const [modalError, setModalError] = useState("");

  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (!query.trim()) {
      setResults([]);
      setState("idle");
      return;
    }

    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => search(query.trim()), 400);

    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, [query]);

  async function search(q: string) {
    setState("loading");
    try {
      if (!config) {
        router.replace("/pair");
        return;
      }
      const data = await api.searchMembers(q, config.museum_id);
      setResults(data);
      setState("done");
    } catch (err: any) {
      const msg =
        err?.body?.full_error_messages ??
        err?.body?.error ??
        "Search failed. Please try again.";
      setErrorMessage(msg);
      setState("error");
    }
  }

  function openModal(member: Member) {
    setSelectedMember(member);
    setModalState("confirm");
    setModalError("");
  }

  function closeModal() {
    if (modalState === "recording") return;
    setSelectedMember(null);
  }

  async function confirmEntry() {
    if (!selectedMember || !config) return;
    setModalState("recording");

    try {
      await api.recordAttendance({
        external_user_id: selectedMember.id,
        location_id: config.location_id,
        scanner_device_id: config.scanner_device_id,
      });
      setModalState("success");
    } catch (err: any) {
      const msg =
        err?.body?.full_error_messages ??
        err?.body?.error ??
        "Failed to record attendance. Please try again.";
      setModalError(msg);
      setModalState("error");
    }
  }

  function isActive(member: Member): boolean {
    if (!member.expires_at || member.expires_at === "Infinity") return true;
    return new Date(member.expires_at) > new Date();
  }

  function renderMember({ item }: { item: Member }) {
    const active = isActive(item);
    return (
      <TouchableOpacity
        style={s.row}
        onPress={() => openModal(item)}
        activeOpacity={0.65}
      >
        <View style={s.rowMain}>
          <Text style={s.rowName}>{item.name}</Text>
          {item.external_id ? (
            <Text style={s.rowSub}>ID: {item.external_id}</Text>
          ) : null}
          {item.membership_level ? (
            <Text style={s.rowSub}>{item.membership_level}</Text>
          ) : null}
        </View>
        <View style={s.rowRight}>
          {item.expires_at && item.expires_at !== "Infinity" ? (
            <Text style={s.rowExpiry}>
              Exp. {new Date(item.expires_at).toLocaleDateString()}
            </Text>
          ) : null}
          <View
            style={[s.statusBadge, active ? s.activeBadge : s.expiredBadge]}
          >
            <Text style={s.statusText}>{active ? "Active" : "Expired"}</Text>
          </View>
        </View>
      </TouchableOpacity>
    );
  }

  return (
    <KeyboardAvoidingView
      style={s.container}
      behavior={Platform.OS === "ios" ? "padding" : undefined}
    >
      <StatusBar barStyle={theme.statusBar} backgroundColor={theme.bg} />

      {/* Header */}
      <View style={s.header}>
        <TextInput
          style={s.searchInput}
          placeholder="Search by name, ID, email, barcode..."
          placeholderTextColor={theme.textPlaceholder}
          value={query}
          onChangeText={setQuery}
          autoFocus
          returnKeyType="search"
          clearButtonMode="while-editing"
          autoCapitalize="none"
          autoCorrect={false}
        />
        <TouchableOpacity style={s.cancelButton} onPress={() => router.back()}>
          <Text style={s.cancelText}>Cancel</Text>
        </TouchableOpacity>
      </View>

      {/* Location picker */}
      {config && (
        <View style={s.locationWrapper}>
          <LocationPicker
            locationName={config.location_name}
            locationId={config.location_id}
            museumId={config.museum_id}
          />
        </View>
      )}

      {/* Body */}
      {state === "idle" && (
        <View style={s.emptyState}>
          <Text style={s.emptyText}>
            Search by name, membership ID, email, barcode or phone number
          </Text>
        </View>
      )}

      {state === "loading" && (
        <View style={s.emptyState}>
          <ActivityIndicator color={theme.textMuted} />
        </View>
      )}

      {state === "error" && (
        <View style={s.emptyState}>
          <Text style={s.errorText}>{errorMessage}</Text>
        </View>
      )}

      {state === "done" && results.length === 0 && (
        <View style={s.emptyState}>
          <Text style={s.emptyText}>No members found for "{query}"</Text>
        </View>
      )}

      {state === "done" && results.length > 0 && (
        <FlatList
          data={results}
          keyExtractor={(item) => String(item.id)}
          renderItem={renderMember}
          contentContainerStyle={s.list}
          keyboardShouldPersistTaps="handled"
        />
      )}

      {/* Entry confirmation modal */}
      <Modal
        visible={!!selectedMember}
        transparent
        animationType="fade"
        onRequestClose={closeModal}
      >
        <Pressable style={s.backdrop} onPress={closeModal}>
          <Pressable style={s.sheet} onPress={() => {}}>
            {selectedMember && modalState === "confirm" && (
              <>
                <Text style={s.sheetTitle}>Allow Entry?</Text>
                <Text style={s.sheetMemberName}>{selectedMember.name}</Text>
                {selectedMember.membership_level ? (
                  <Text style={s.sheetMeta}>
                    {selectedMember.membership_level}
                  </Text>
                ) : null}
                {selectedMember.external_id ? (
                  <Text style={s.sheetMeta}>
                    ID: {selectedMember.external_id}
                  </Text>
                ) : null}
                {!isActive(selectedMember) && (
                  <View style={s.expiredWarning}>
                    <Text style={s.expiredWarningText}>
                      This membership is expired
                    </Text>
                  </View>
                )}
                <View style={s.sheetActions}>
                  <TouchableOpacity style={s.cancelAction} onPress={closeModal}>
                    <Text style={s.cancelActionText}>Cancel</Text>
                  </TouchableOpacity>
                  <TouchableOpacity
                    style={[
                      s.allowAction,
                      !isActive(selectedMember) && s.allowActionExpired,
                    ]}
                    onPress={confirmEntry}
                  >
                    <Text style={s.allowActionText}>Allow Entry</Text>
                  </TouchableOpacity>
                </View>
              </>
            )}

            {modalState === "recording" && (
              <>
                <ActivityIndicator color={theme.textPrimary} size="large" />
                <Text style={s.sheetStatusText}>Recording attendance...</Text>
              </>
            )}

            {modalState === "success" && selectedMember && (
              <>
                <Text style={s.sheetSuccessIcon}>✓</Text>
                <Text style={s.sheetTitle}>Entry Recorded</Text>
                <Text style={s.sheetMemberName}>{selectedMember.name}</Text>
                <TouchableOpacity style={s.doneAction} onPress={closeModal}>
                  <Text style={s.doneActionText}>Done</Text>
                </TouchableOpacity>
              </>
            )}

            {modalState === "error" && (
              <>
                <Text style={s.sheetErrorIcon}>✕</Text>
                <Text style={s.sheetTitle}>Error</Text>
                <Text style={s.sheetErrorText}>{modalError}</Text>
                <View style={s.sheetActions}>
                  <TouchableOpacity style={s.cancelAction} onPress={closeModal}>
                    <Text style={s.cancelActionText}>Dismiss</Text>
                  </TouchableOpacity>
                  <TouchableOpacity
                    style={s.allowAction}
                    onPress={confirmEntry}
                  >
                    <Text style={s.allowActionText}>Retry</Text>
                  </TouchableOpacity>
                </View>
              </>
            )}
          </Pressable>
        </Pressable>
      </Modal>
    </KeyboardAvoidingView>
  );
}

const styles = (t: Theme) =>
  StyleSheet.create({
    container: {
      flex: 1,
      backgroundColor: t.bg,
    },
    header: {
      flexDirection: "row",
      alignItems: "center",
      paddingTop: 56,
      paddingHorizontal: 16,
      paddingBottom: 12,
      gap: 10,
      borderBottomWidth: 1,
      borderBottomColor: t.separator,
    },
    searchInput: {
      flex: 1,
      backgroundColor: t.bgCard,
      borderRadius: 12,
      paddingHorizontal: 14,
      paddingVertical: 10,
      fontSize: 16,
      color: t.textPrimary,
    },
    cancelButton: {
      paddingVertical: 8,
      paddingHorizontal: 4,
    },
    cancelText: {
      color: t.textMuted,
      fontSize: 15,
    },
    locationWrapper: {
      paddingHorizontal: 16,
      paddingTop: 10,
      paddingBottom: 2,
      borderBottomWidth: 1,
      borderBottomColor: t.separator,
    },
    emptyState: {
      flex: 1,
      alignItems: "center",
      justifyContent: "center",
      padding: 32,
    },
    emptyText: {
      color: t.textMuted,
      fontSize: 15,
      textAlign: "center",
      lineHeight: 22,
    },
    errorText: {
      color: "#f87171",
      fontSize: 15,
      textAlign: "center",
    },
    list: {
      paddingVertical: 8,
    },
    row: {
      flexDirection: "row",
      alignItems: "center",
      paddingHorizontal: 20,
      paddingVertical: 14,
      borderBottomWidth: 1,
      borderBottomColor: t.separator,
    },
    rowMain: {
      flex: 1,
      gap: 2,
    },
    rowName: {
      fontSize: 16,
      fontWeight: "600",
      color: t.textPrimary,
    },
    rowSub: {
      fontSize: 13,
      color: t.textMuted,
    },
    rowRight: {
      alignItems: "flex-end",
      gap: 4,
    },
    rowExpiry: {
      fontSize: 12,
      color: t.textMuted,
    },
    statusBadge: {
      borderRadius: 6,
      paddingHorizontal: 8,
      paddingVertical: 3,
    },
    activeBadge: {
      backgroundColor: "#14532d",
    },
    expiredBadge: {
      backgroundColor: "#7f1d1d",
    },
    statusText: {
      fontSize: 11,
      fontWeight: "700",
      color: "#fff",
    },
    // Modal
    backdrop: {
      flex: 1,
      backgroundColor: "rgba(0,0,0,0.65)",
      justifyContent: "flex-end",
    },
    sheet: {
      backgroundColor: t.bgModal,
      borderTopLeftRadius: 20,
      borderTopRightRadius: 20,
      padding: 28,
      paddingBottom: 40,
      alignItems: "center",
      gap: 8,
      borderTopWidth: 1,
      borderColor: t.border,
    },
    sheetTitle: {
      fontSize: 20,
      fontWeight: "700",
      color: t.textPrimary,
      marginBottom: 4,
    },
    sheetMemberName: {
      fontSize: 18,
      fontWeight: "600",
      color: t.textSecondary,
    },
    sheetMeta: {
      fontSize: 14,
      color: t.textMuted,
    },
    expiredWarning: {
      backgroundColor: "#7f1d1d",
      borderRadius: 8,
      paddingHorizontal: 14,
      paddingVertical: 6,
      marginTop: 4,
    },
    expiredWarningText: {
      color: "#fca5a5",
      fontSize: 13,
      fontWeight: "600",
    },
    sheetActions: {
      flexDirection: "row",
      gap: 12,
      marginTop: 16,
      width: "100%",
    },
    cancelAction: {
      flex: 1,
      paddingVertical: 14,
      borderRadius: 12,
      borderWidth: 1,
      borderColor: t.border,
      alignItems: "center",
    },
    cancelActionText: {
      color: t.textMuted,
      fontSize: 16,
      fontWeight: "600",
    },
    allowAction: {
      flex: 1,
      paddingVertical: 14,
      borderRadius: 12,
      backgroundColor: "#14532d",
      alignItems: "center",
    },
    allowActionExpired: {
      backgroundColor: "#78350f",
    },
    allowActionText: {
      color: "#fff",
      fontSize: 16,
      fontWeight: "700",
    },
    sheetStatusText: {
      color: t.textMuted,
      fontSize: 15,
      marginTop: 12,
    },
    sheetSuccessIcon: {
      fontSize: 48,
      color: "#4ade80",
      marginBottom: 4,
    },
    sheetErrorIcon: {
      fontSize: 48,
      color: "#f87171",
      marginBottom: 4,
    },
    sheetErrorText: {
      color: "#f87171",
      fontSize: 14,
      textAlign: "center",
    },
    doneAction: {
      marginTop: 16,
      width: "100%",
      paddingVertical: 14,
      borderRadius: 12,
      backgroundColor: "#1e3a5f",
      alignItems: "center",
    },
    doneActionText: {
      color: "#fff",
      fontSize: 16,
      fontWeight: "700",
    },
  });
