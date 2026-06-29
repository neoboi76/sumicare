/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

export interface BookingResponse {
  id: string;
  reference?: string | null;
  clientNickname: string;
  clientEmail?: string | null;
  lockerNumber: string | null;
  serviceId: number;
  reservationType: string;
  scheduledAt: string;
  projectedEndAt: string;
  status: string;
  clientGender?: string | null;
  orderId?: string | null;
  orderStatus?: string | null;
  treatmentSlipId?: string | null;
  pax?: number | null;
  sessionExtended?: boolean;
  remarks?: string | null;
  preferredTherapist?: string | null;
  preferredRoomId?: string | null;
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
  status: string;
}
