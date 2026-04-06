import { useEffect, useState } from "react";
import * as SecureStore from "expo-secure-store";

const DEVICE_CONFIG_KEY = "cuseum_device_config";

export interface DeviceConfig {
  scanner_device_id: number;
  museum_id: number;
  museum_name: string;
  access_token: string;
  refresh_token: string;
  token_expires_at: number; // Unix timestamp (ms)
  location_id: number | null;
  location_name: string | null;
}

// ---------------------------------------------------------------------------
// In-memory cache + subscribers
// ---------------------------------------------------------------------------

let _cache: DeviceConfig | null = null;
const _listeners = new Set<(config: DeviceConfig | null) => void>();

function notify() {
  _listeners.forEach((fn) => fn(_cache));
}

// ---------------------------------------------------------------------------
// Store
// ---------------------------------------------------------------------------

export const deviceStore = {
  async save(config: DeviceConfig): Promise<void> {
    _cache = config;
    await SecureStore.setItemAsync(DEVICE_CONFIG_KEY, JSON.stringify(config));
    notify();
  },

  async load(): Promise<DeviceConfig | null> {
    if (_cache) return _cache;
    const raw = await SecureStore.getItemAsync(DEVICE_CONFIG_KEY);
    if (!raw) return null;
    _cache = JSON.parse(raw) as DeviceConfig;
    return _cache;
  },

  async update(patch: Partial<DeviceConfig>): Promise<void> {
    const current = await this.load();
    if (!current) return;
    await this.save({ ...current, ...patch });
  },

  async clear(): Promise<void> {
    _cache = null;
    await SecureStore.deleteItemAsync(DEVICE_CONFIG_KEY);
    notify();
  },

  subscribe(fn: (config: DeviceConfig | null) => void): () => void {
    _listeners.add(fn);
    return () => _listeners.delete(fn);
  },
};

// ---------------------------------------------------------------------------
// React hook — always reflects the latest config across all screens
// ---------------------------------------------------------------------------

export function useDeviceConfig(): DeviceConfig | null {
  const [config, setConfig] = useState<DeviceConfig | null>(_cache);

  useEffect(() => {
    // Hydrate from SecureStore on first mount if cache is empty
    if (!_cache) {
      deviceStore.load().then(setConfig);
    }

    const unsubscribe = deviceStore.subscribe(setConfig);
    return unsubscribe;
  }, []);

  return config;
}
