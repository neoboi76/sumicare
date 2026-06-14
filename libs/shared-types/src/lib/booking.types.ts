export type ReservationType = 'HARD' | 'SOFT';
export type BookingStatus = 'PENDING' | 'ACTIVE' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW';
export type SessionStatus = 'ACTIVE' | 'COMPLETED' | 'CANCELLED';

export interface CreateBookingRequest {
  clientId?: string;
  clientNickname: string;
  lockerNumber?: string;
  serviceId: number;
  reservationType: ReservationType;
  scheduledAt: string;
}

export interface BookingResponse {
  id: string;
  clientNickname: string;
  lockerNumber: string | null;
  serviceId: number;
  reservationType: ReservationType;
  scheduledAt: string;
  effectiveStartAt: string;
  projectedEndAt: string;
  status: BookingStatus;
}

export interface StartSessionRequest {
  primaryTherapistId?: string;
  secondaryTherapistId?: string;
  roomId?: string;
  bedId?: string;
  specificallyRequested: boolean;
}

export interface SessionResponse {
  id: string;
  bookingId: string;
  primaryTherapistId: string | null;
  secondaryTherapistId: string | null;
  roomId: string | null;
  bedId: string | null;
  specificallyRequested: boolean;
  extension: boolean;
  extensionMinutes: number;
  startedAt: string | null;
  endedAt: string | null;
  status: SessionStatus;
}
