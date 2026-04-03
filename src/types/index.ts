export interface DeviceConfig {
  scanner_device_id: number;
  museum_id: number;
  museum_name: string;
  access_token: string;
}

export interface Location {
  id: number;
  name: string;
  museum_id: number;
  default: boolean;
}

export interface Member {
  id: number;
  name: string;
  external_id: string;
  expires_at: string | null;
  membership_level: string | null;
}

export type ValidationReason = "active" | "expired" | "no_card";

export interface ValidationResult {
  external_user_id: number;
  member: Member;
  membership_active: boolean;
  reason: ValidationReason;
}
