import { useColorScheme } from "react-native";

export interface Theme {
  // Backgrounds
  bg: string; // screen background
  bgCard: string; // cards / input backgrounds
  bgCard2: string; // secondary card (slightly lighter/darker)
  bgModal: string; // bottom-sheet background

  // Text
  textPrimary: string;
  textSecondary: string;
  textMuted: string;
  textPlaceholder: string;

  // Borders & separators
  border: string;
  separator: string;

  // Interactive
  scanBtnBg: string; // primary "Scan Card" button background
  scanBtnText: string; // primary button text
  searchBtnBg: string; // secondary "Search Member" button background
  searchBtnBorder: string;
  searchBtnText: string;

  // Accents (same in both modes)
  green: string;
  greenBg: string;
  greenBgDeep: string;

  // Status bar style
  statusBar: "light-content" | "dark-content";
}

const dark: Theme = {
  bg: "#111111",
  bgCard: "#1e1e1e",
  bgCard2: "#2a2a2a",
  bgModal: "#1a1a1a",

  textPrimary: "#ffffff",
  textSecondary: "#e5e5e5",
  textMuted: "#777777",
  textPlaceholder: "#555555",

  border: "#333333",
  separator: "#222222",

  scanBtnBg: "#ffffff",
  scanBtnText: "#111111",
  searchBtnBg: "#1e1e1e",
  searchBtnBorder: "#333333",
  searchBtnText: "#ffffff",

  green: "#4ade80",
  greenBg: "#14532d",
  greenBgDeep: "#0f2d1a",

  statusBar: "light-content",
};

const light: Theme = {
  bg: "#f2f2f7",
  bgCard: "#ffffff",
  bgCard2: "#f0f0f0",
  bgModal: "#ffffff",

  textPrimary: "#111111",
  textSecondary: "#222222",
  textMuted: "#888888",
  textPlaceholder: "#aaaaaa",

  border: "#dddddd",
  separator: "#e5e5e5",

  scanBtnBg: "#111111",
  scanBtnText: "#ffffff",
  searchBtnBg: "#ffffff",
  searchBtnBorder: "#dddddd",
  searchBtnText: "#111111",

  green: "#16a34a",
  greenBg: "#dcfce7",
  greenBgDeep: "#bbf7d0",

  statusBar: "dark-content",
};

export function useTheme(): Theme {
  const scheme = useColorScheme();
  return scheme === "dark" ? dark : light;
}
