/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

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
