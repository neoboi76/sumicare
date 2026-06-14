import { Role } from './auth.types';

export interface UserResponse {
  id: string;
  organizationId: string;
  username: string;
  email: string | null;
  role: Role;
  displayName: string | null;
  active: boolean;
  createdAt: string;
}

export interface CreateUserRequest {
  username: string;
  email?: string;
  password: string;
  role: Role;
  displayName?: string;
}

export interface UpdateUserRequest {
  email?: string;
  password?: string;
  role?: Role;
  displayName?: string;
  active?: boolean;
}
