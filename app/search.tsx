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
import { deviceStore } from "../src/store/deviceStore";
import { api } from "../src/api/client";
import type { Member } from "../src/types";

type SearchState = "idle" | "loading" | "done" | "error";
type ModalState = "confirm" | "recording" | "success" | "error";

export default function SearchScreen() {
  const router = useRouter();
  const [query, setQuery] = useState("");
  const [state, setState] = useState<SearchState>("idle");
  const [results, setResults] = useState<Member[]>([]);
  const [errorMessage, setErrorMessage] = useState("");

  // Modal state
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
      const config = await deviceStore.load();
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
    if (modalState === "recording") return; // don't close while in-flight
    setSelectedMember(null);
  }

  async function confirmEntry() {
    if (!selectedMember) return;
    setModalState("recording");

    try {
      const config = await deviceStore.load();
      if (!config) {
        router.replace("/pair");
        return;
      }
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
        style={styles.row}
        onPress={() => openModal(item)}
        activeOpacity={0.65}
      >
        <View style={styles.rowMain}>
          <Text style={styles.rowName}>{item.name}</Text>
          {item.external_id ? (
            <Text style={styles.rowSub}>ID: {item.external_id}</Text>
          ) : null}
          {item.membership_level ? (
            <Text style={styles.rowSub}>{item.membership_level}</Text>
          ) : null}
        </View>
        <View style={styles.rowRight}>
          {item.expires_at && item.expires_at !== "Infinity" ? (
            <Text style={styles.rowExpiry}>
              Exp. {new Date(item.expires_at).toLocaleDateString()}
            </Text>
          ) : null}
          <View
            style={[
              styles.statusBadge,
              active ? styles.activeBadge : styles.expiredBadge,
            ]}
          >
            <Text style={styles.statusText}>
              {active ? "Active" : "Expired"}
            </Text>
          </View>
        </View>
      </TouchableOpacity>
    );
  }

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === "ios" ? "padding" : undefined}
    >
      <StatusBar barStyle="light-content" />

      {/* Header */}
      <View style={styles.header}>
        <TextInput
          style={styles.searchInput}
          placeholder="Search by name, ID, email, barcode..."
          placeholderTextColor="#666"
          value={query}
          onChangeText={setQuery}
          autoFocus
          returnKeyType="search"
          clearButtonMode="while-editing"
          autoCapitalize="none"
          autoCorrect={false}
        />
        <TouchableOpacity
          style={styles.cancelButton}
          onPress={() => router.back()}
        >
          <Text style={styles.cancelText}>Cancel</Text>
        </TouchableOpacity>
      </View>

      {/* Body */}
      {state === "idle" && (
        <View style={styles.emptyState}>
          <Text style={styles.emptyText}>
            Search by name, membership ID, email, barcode or phone number
          </Text>
        </View>
      )}

      {state === "loading" && (
        <View style={styles.emptyState}>
          <ActivityIndicator color="#fff" />
        </View>
      )}

      {state === "error" && (
        <View style={styles.emptyState}>
          <Text style={styles.errorText}>{errorMessage}</Text>
        </View>
      )}

      {state === "done" && results.length === 0 && (
        <View style={styles.emptyState}>
          <Text style={styles.emptyText}>No members found for "{query}"</Text>
        </View>
      )}

      {state === "done" && results.length > 0 && (
        <FlatList
          data={results}
          keyExtractor={(item) => String(item.id)}
          renderItem={renderMember}
          contentContainerStyle={styles.list}
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
        <Pressable style={styles.backdrop} onPress={closeModal}>
          <Pressable style={styles.sheet} onPress={() => {}}>
            {selectedMember && modalState === "confirm" && (
              <>
                <Text style={styles.sheetTitle}>Allow Entry?</Text>
                <Text style={styles.sheetMemberName}>
                  {selectedMember.name}
                </Text>
                {selectedMember.membership_level ? (
                  <Text style={styles.sheetMeta}>
                    {selectedMember.membership_level}
                  </Text>
                ) : null}
                {selectedMember.external_id ? (
                  <Text style={styles.sheetMeta}>
                    ID: {selectedMember.external_id}
                  </Text>
                ) : null}
                {!isActive(selectedMember) && (
                  <View style={styles.expiredWarning}>
                    <Text style={styles.expiredWarningText}>
                      This membership is expired
                    </Text>
                  </View>
                )}
                <View style={styles.sheetActions}>
                  <TouchableOpacity
                    style={styles.cancelAction}
                    onPress={closeModal}
                  >
                    <Text style={styles.cancelActionText}>Cancel</Text>
                  </TouchableOpacity>
                  <TouchableOpacity
                    style={[
                      styles.allowAction,
                      !isActive(selectedMember) && styles.allowActionExpired,
                    ]}
                    onPress={confirmEntry}
                  >
                    <Text style={styles.allowActionText}>Allow Entry</Text>
                  </TouchableOpacity>
                </View>
              </>
            )}

            {modalState === "recording" && (
              <>
                <ActivityIndicator color="#fff" size="large" />
                <Text style={styles.sheetStatusText}>
                  Recording attendance...
                </Text>
              </>
            )}

            {modalState === "success" && selectedMember && (
              <>
                <Text style={styles.sheetSuccessIcon}>✓</Text>
                <Text style={styles.sheetTitle}>Entry Recorded</Text>
                <Text style={styles.sheetMemberName}>
                  {selectedMember.name}
                </Text>
                <TouchableOpacity
                  style={styles.doneAction}
                  onPress={closeModal}
                >
                  <Text style={styles.doneActionText}>Done</Text>
                </TouchableOpacity>
              </>
            )}

            {modalState === "error" && (
              <>
                <Text style={styles.sheetErrorIcon}>✕</Text>
                <Text style={styles.sheetTitle}>Error</Text>
                <Text style={styles.sheetErrorText}>{modalError}</Text>
                <View style={styles.sheetActions}>
                  <TouchableOpacity
                    style={styles.cancelAction}
                    onPress={closeModal}
                  >
                    <Text style={styles.cancelActionText}>Dismiss</Text>
                  </TouchableOpacity>
                  <TouchableOpacity
                    style={styles.allowAction}
                    onPress={confirmEntry}
                  >
                    <Text style={styles.allowActionText}>Retry</Text>
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

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#111",
  },
  header: {
    flexDirection: "row",
    alignItems: "center",
    paddingTop: 56,
    paddingHorizontal: 16,
    paddingBottom: 12,
    gap: 10,
    borderBottomWidth: 1,
    borderBottomColor: "#222",
  },
  searchInput: {
    flex: 1,
    backgroundColor: "#1e1e1e",
    borderRadius: 12,
    paddingHorizontal: 14,
    paddingVertical: 10,
    fontSize: 16,
    color: "#fff",
  },
  cancelButton: {
    paddingVertical: 8,
    paddingHorizontal: 4,
  },
  cancelText: {
    color: "#aaa",
    fontSize: 15,
  },
  emptyState: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    padding: 32,
  },
  emptyText: {
    color: "#555",
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
    borderBottomColor: "#1e1e1e",
  },
  rowMain: {
    flex: 1,
    gap: 2,
  },
  rowName: {
    fontSize: 16,
    fontWeight: "600",
    color: "#fff",
  },
  rowSub: {
    fontSize: 13,
    color: "#777",
  },
  rowRight: {
    alignItems: "flex-end",
    gap: 4,
  },
  rowExpiry: {
    fontSize: 12,
    color: "#666",
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
    backgroundColor: "#1a1a1a",
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    padding: 28,
    paddingBottom: 40,
    alignItems: "center",
    gap: 8,
  },
  sheetTitle: {
    fontSize: 20,
    fontWeight: "700",
    color: "#fff",
    marginBottom: 4,
  },
  sheetMemberName: {
    fontSize: 18,
    fontWeight: "600",
    color: "#e5e5e5",
  },
  sheetMeta: {
    fontSize: 14,
    color: "#777",
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
    borderColor: "#333",
    alignItems: "center",
  },
  cancelActionText: {
    color: "#aaa",
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
    color: "#aaa",
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
