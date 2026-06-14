export type Gender = 'M' | 'F';

export interface TherapistResponse {
  id: string;
  staffNumber: string | null;
  nickname: string;
  gender: Gender;
  backup: boolean;
  active: boolean;
}

export interface CreateTherapistRequest {
  staffNumber?: string;
  nickname: string;
  gender: Gender;
  backup: boolean;
}
