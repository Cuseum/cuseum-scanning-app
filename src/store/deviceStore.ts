import * as SecureStore from "expo-secure-store";

const DEVICE_CONFIG_KEY = "cuseum_device_config";

export interface DeviceConfig {
  scanner_device_id: number;
  museum_id: number;
  museum_name: string;
  access_token: string;
  refresh_token: string;
  token_expires_at: number; // Unix timestamp (ms)
  location_id: number;
  location_name: string;
}

export const deviceStore = {
  async save(config: DeviceConfig): Promise<void> {
    await SecureStore.setItemAsync(DEVICE_CONFIG_KEY, JSON.stringify(config));
  },

  async load(): Promise<DeviceConfig | null> {
    const raw = await SecureStore.getItemAsync(DEVICE_CONFIG_KEY);
    if (!raw) return null;
    return JSON.parse(raw) as DeviceConfig;
  },

  async clear(): Promise<void> {
    await SecureStore.deleteItemAsync(DEVICE_CONFIG_KEY);
  },
};
