export interface LoginRequest {
  username: string;
  password: string;
}

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  role: Role;
}

export type Role = 'SUPERADMIN' | 'ADMIN' | 'MANAGER' | 'RECEPTIONIST' | 'STAFF';
