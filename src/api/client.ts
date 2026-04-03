const BASE_URL = process.env.EXPO_PUBLIC_API_URL ?? "http://10.0.2.2:3000";
const CLIENT_ID = process.env.EXPO_PUBLIC_OAUTH_CLIENT_ID ?? "";
const CLIENT_SECRET = process.env.EXPO_PUBLIC_OAUTH_CLIENT_SECRET ?? "";
const USERNAME = process.env.EXPO_PUBLIC_OAUTH_USERNAME ?? "";
const PASSWORD = process.env.EXPO_PUBLIC_OAUTH_PASSWORD ?? "";

async function request<T>(
  path: string,
  options: RequestInit & { token?: string } = {}
): Promise<T> {
  const { token, ...fetchOptions } = options;

  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    Accept: "application/json",
    ...(fetchOptions.headers as Record<string, string>),
  };

  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  const response = await fetch(`${BASE_URL}${path}`, {
    ...fetchOptions,
    headers,
  });

  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    throw { status: response.status, body };
  }

  return response.json() as Promise<T>;
}

export interface TokenResponse {
  access_token: string;
  token_type: string;
  expires_in: number;
}

export interface PairResponse {
  success: boolean;
  scanner_device_id: number;
  paired: boolean;
  museum_id: number;
  museum_name: string;
}

export const api = {
  async getToken(): Promise<TokenResponse> {
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
  },

  pairDevice(id: number, token: string): Promise<PairResponse> {
    return request<PairResponse>(`/api/v5/scanner_devices/${id}/pair`, {
      method: "PATCH",
      token,
    });
  },

  getLocations(museumId: number, token: string) {
    return request<{ id: number; name: string; museum_id: number }[]>(
      `/api/v5/locations?museum_id=${museumId}`,
      { token }
    );
  },

  validateCard(barcode: string, token: string) {
    return request("/api/v5/pass_books/validate", {
      method: "POST",
      token,
      body: JSON.stringify({ barcode }),
    });
  },

  recordAttendance(
    params: {
      external_user_id: number;
      location_id: number;
      scanner_device_id: number;
    },
    token: string
  ) {
    return request("/api/v5/attendance_tracking", {
      method: "POST",
      token,
      body: JSON.stringify(params),
    });
  },

  searchMembers(query: string, token: string) {
    const params = new URLSearchParams({ "f[name_cont]": query });
    return request(`/api/v5/external_users?${params}`, { token });
  },
};
