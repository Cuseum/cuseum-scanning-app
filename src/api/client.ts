import { deviceStore, DeviceConfig } from "../store/deviceStore";

const BASE_URL = process.env.EXPO_PUBLIC_API_URL ?? "http://10.0.2.2:3000";
const CLIENT_ID = process.env.EXPO_PUBLIC_OAUTH_CLIENT_ID ?? "";
const CLIENT_SECRET = process.env.EXPO_PUBLIC_OAUTH_CLIENT_SECRET ?? "";
const USERNAME = process.env.EXPO_PUBLIC_OAUTH_USERNAME ?? "";
const PASSWORD = process.env.EXPO_PUBLIC_OAUTH_PASSWORD ?? "";

// How many ms before actual expiry we treat the token as stale (60 s buffer)
const EXPIRY_BUFFER_MS = 60_000;

// Prevents concurrent refresh races
let refreshPromise: Promise<DeviceConfig> | null = null;

// ---------------------------------------------------------------------------
// Public interfaces
// ---------------------------------------------------------------------------

export interface TokenResponse {
  access_token: string;
  token_type: string;
  expires_in: number;
  refresh_token: string;
}

export interface PairResponse {
  success: boolean;
  scanner_device_id: number;
  paired: boolean;
  museum_id: number;
  museum_name: string;
}

// ---------------------------------------------------------------------------
// Internal token helpers
// ---------------------------------------------------------------------------

async function fetchNewToken(): Promise<TokenResponse> {
  const body = new URLSearchParams({
    grant_type: "password",
    client_id: CLIENT_ID,
    client_secret: CLIENT_SECRET,
    username: USERNAME,
    password: PASSWORD,
    scope:
      "auth_master_admin read_locations write_attendances read_pass_books read_external_users",
  });

  const response = await fetch(`${BASE_URL}/oauth/token`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: body.toString(),
  });

  if (!response.ok) {
    const err = await response.json().catch(() => ({}));
    throw { status: response.status, body: err };
  }

  return response.json() as Promise<TokenResponse>;
}

async function fetchRefreshedToken(
  refreshToken: string
): Promise<TokenResponse> {
  const body = new URLSearchParams({
    grant_type: "refresh_token",
    client_id: CLIENT_ID,
    client_secret: CLIENT_SECRET,
    refresh_token: refreshToken,
  });

  const response = await fetch(`${BASE_URL}/oauth/token`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: body.toString(),
  });

  if (!response.ok) {
    const err = await response.json().catch(() => ({}));
    throw { status: response.status, body: err };
  }

  return response.json() as Promise<TokenResponse>;
}

/**
 * Returns a valid DeviceConfig, proactively refreshing the access token when
 * it is within EXPIRY_BUFFER_MS of expiry.  Pass forceRefresh=true to refresh
 * unconditionally (used after a 401 response).  Serialises concurrent calls so
 * only one token request goes out at a time.
 */
async function getValidConfig(forceRefresh = false): Promise<DeviceConfig> {
  const config = await deviceStore.load();
  if (!config) throw { status: 401, body: { error: "Device not paired" } };

  const isExpired =
    forceRefresh ||
    !config.token_expires_at ||
    Date.now() >= config.token_expires_at - EXPIRY_BUFFER_MS;

  if (!isExpired) return config;

  if (!refreshPromise) {
    refreshPromise = (async (): Promise<DeviceConfig> => {
      try {
        let tokenResp: TokenResponse;
        if (config.refresh_token) {
          try {
            tokenResp = await fetchRefreshedToken(config.refresh_token);
          } catch {
            // Refresh token expired — fall back to password grant
            tokenResp = await fetchNewToken();
          }
        } else {
          tokenResp = await fetchNewToken();
        }

        const updated: DeviceConfig = {
          ...config,
          access_token: tokenResp.access_token,
          refresh_token: tokenResp.refresh_token,
          token_expires_at: Date.now() + tokenResp.expires_in * 1000,
        };
        await deviceStore.save(updated);
        return updated;
      } finally {
        refreshPromise = null;
      }
    })();
  }

  return refreshPromise;
}

// ---------------------------------------------------------------------------
// Core authenticated request — auto-injects a valid token
// ---------------------------------------------------------------------------

async function request<T>(
  path: string,
  options: RequestInit = {},
  _config?: DeviceConfig
): Promise<T> {
  const config = _config ?? (await getValidConfig());

  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    Accept: "application/json",
    Authorization: `Bearer ${config.access_token}`,
    ...(options.headers as Record<string, string>),
  };

  const response = await fetch(`${BASE_URL}${path}`, { ...options, headers });

  if (response.status === 401 && !_config) {
    // Server rejected token — force refresh and retry exactly once
    const refreshed = await getValidConfig(true);
    return request<T>(path, options, refreshed);
  }

  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    throw { status: response.status, body };
  }

  return response.json() as Promise<T>;
}

// ---------------------------------------------------------------------------
// Pairing helpers (no stored config available yet)
// ---------------------------------------------------------------------------

async function authedFetch<T>(
  url: string,
  token: string,
  init: RequestInit = {}
): Promise<T> {
  const response = await fetch(url, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
      Authorization: `Bearer ${token}`,
      ...(init.headers as Record<string, string>),
    },
  });
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    throw { status: response.status, body };
  }
  return response.json() as Promise<T>;
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

export const api = {
  /** Fetch a brand-new password-grant token. Used only during pairing. */
  getToken(): Promise<TokenResponse> {
    return fetchNewToken();
  },

  /** Pair a scanner device. Uses a raw token (called before config is stored). */
  pairDevice(id: number, token: string): Promise<PairResponse> {
    return authedFetch<PairResponse>(
      `${BASE_URL}/api/v5/scanner_devices/${id}/pair`,
      token,
      { method: "PATCH" }
    );
  },

  /** Fetch locations with an explicit token (called during pairing, before config is stored). */
  getLocations(
    museumId: number,
    token: string
  ): Promise<{ id: number; name: string; museum_id: number }[]> {
    return authedFetch(
      `${BASE_URL}/api/v5/locations?museum_id=${museumId}`,
      token
    );
  },

  /** Fetch locations for the paired museum. Token auto-managed. */
  getLocationsForMuseum(
    museumId: number
  ): Promise<{ id: number; name: string; museum_id: number }[]> {
    return request(`/api/v5/locations?museum_id=${museumId}`);
  },

  /** Validate a membership card barcode. Token auto-managed. */
  validateCard(barcode: string) {
    return request("/api/v5/pass_books/validate", {
      method: "POST",
      body: JSON.stringify({ barcode }),
    });
  },

  /** Record an attendance entry. Token auto-managed. */
  recordAttendance(params: {
    external_user_id: number;
    location_id: number;
    scanner_device_id: number;
  }) {
    return request("/api/v5/attendance_tracking", {
      method: "POST",
      body: JSON.stringify(params),
    });
  },

  /** Search members by name/email/id/barcode etc. Token auto-managed. */
  searchMembers(query: string, museumId: number) {
    const params = new URLSearchParams({
      "f[museum_id_eq]": String(museumId),
      "f[name_or_email_address_or_external_id_or_member_number_or_barcode_or_phone_number_cont]":
        query,
    });
    return request<import("../types").Member[]>(
      `/api/v5/external_users?${params}`
    );
  },

  /** Unpair this scanner device in the CMS. Token auto-managed. */
  unpairDevice(scannerDeviceId: number) {
    return request(`/api/v5/scanner_devices/${scannerDeviceId}/unpair`, {
      method: "PATCH",
    });
  },
};
