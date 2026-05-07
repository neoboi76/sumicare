import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { environment } from '../../../../environments/environment';

interface BookingResponse {
  id: string;
  clientNickname: string;
  lockerNumber: string | null;
  serviceId: number;
  reservationType: string;
  scheduledAt: string;
  effectiveStartAt: string;
  projectedEndAt: string;
  status: string;
}

interface SessionResponse {
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

interface ServiceItem {
  id: number;
  name: string;
  durationMinutes: number;
  price: number;
  requiresTwoTherapists: boolean;
  vip: boolean;
}

interface LineupTherapist {
  therapistId: string;
  nickname: string;
  gender: string;
  shiftLabel: string | null;
  flag: string;
  skipped: boolean;
  position: number;
}

interface BedItem {
  id: string;
  label: string;
  rowIndex: number | null;
  occupancy: Record<string, string>;
}

interface RoomItem {
  id: string;
  roomNumber: string;
  floor: number | null;
  roomType: string;
  rowSegmented: boolean;
  beds: BedItem[];
}

interface WalkInResponse {
  slipId: string;
  bookingId: string;
  sessionId: string;
  tsn: string;
}

@Component({
  selector: 'sumi-bookings',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './bookings.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class BookingsComponent implements OnInit {
  private http = inject(HttpClient);
  private router = inject(Router);

  selectedDate = signal(new Date().toISOString().slice(0, 10));
  bookings = signal<BookingResponse[]>([]);
  services = signal<ServiceItem[]>([]);
  lineup = signal<LineupTherapist[]>([]);
  rooms = signal<RoomItem[]>([]);
  showWalkIn = signal(false);
  walkInError = signal<string | null>(null);
  walkInSubmitting = signal(false);
  startBooking = signal<BookingResponse | null>(null);

  walkInNickname = '';
  walkInLocker = '';
  walkInServiceId = signal<number>(0);
  walkInReservationType = 'WALK_IN';
  walkInPax = 1;
  walkInStartTime = this.nowDatetimeLocal();
  walkInEndTimeOverride = '';
  walkInPrimaryTherapistId: string | null = null;
  walkInSecondaryTherapistId: string | null = null;
  walkInRoomId = signal<string | null>(null);
  walkInBedId: string | null = null;
  walkInSpecificallyRequested = false;
  walkInJacuzziMinutes = 60;
  walkInMassageMinutes = 60;
  walkInWine = false;
  walkInOrNumber = '';
  walkInAddOnOrNumber = '';
  walkInOthersAddOn = '';
  walkInRemarks = '';
  walkInTotalAmount: number | null = null;
  walkInWaiver = false;

  startPrimaryTherapistId = signal<string | null>(null);
  startSecondaryTherapistId = signal<string | null>(null);
  startRoomId = signal<string | null>(null);
  startBedId = signal<string | null>(null);
  startSpecificallyRequested = signal(false);

  selectedWalkInService = computed(() => {
    const id = this.walkInServiceId();
    return this.services().find(s => s.id === id) ?? null;
  });

  isWalkInVip = computed(() => this.selectedWalkInService()?.vip ?? false);

  walkInComputedEnd = computed(() => {
    const svc = this.selectedWalkInService();
    if (!svc || !this.walkInStartTime) return '';
    const start = new Date(this.walkInStartTime);
    if (isNaN(start.getTime())) return '';
    const end = new Date(start.getTime() + svc.durationMinutes * 60_000);
    return this.toDatetimeLocal(end);
  });

  walkInBedsForRoom = computed(() => {
    const roomId = this.walkInRoomId();
    if (!roomId) return [];
    return this.rooms().find(r => r.id === roomId)?.beds ?? [];
  });

  pickedRoomNumber = computed(() => {
    const room = this.rooms().find(r => r.id === this.startRoomId());
    return room?.roomNumber ?? '';
  });

  pickedBedLabel = computed(() => {
    const room = this.rooms().find(r => r.id === this.startRoomId());
    return room?.beds.find(b => b.id === this.startBedId())?.label ?? '';
  });

  needsSecondary = computed(() => {
    const id = this.startBooking()?.serviceId;
    if (!id) return false;
    return this.services().find(s => s.id === id)?.requiresTwoTherapists ?? false;
  });

  walkInNeedsSecondary = computed(() => this.selectedWalkInService()?.requiresTwoTherapists ?? false);

  canStart = computed(() =>
    this.startPrimaryTherapistId() !== null &&
    this.startRoomId() !== null &&
    this.startBedId() !== null
  );

  ngOnInit(): void {
    this.reload();
    this.loadReference();
  }

  onDateChange(value: string): void {
    this.selectedDate.set(value);
    this.reload();
  }

  reload(): void {
    const d = this.selectedDate();
    const start = `${d}T00:00:00.000Z`;
    const end = `${d}T23:59:59.999Z`;
    const params = `?from=${encodeURIComponent(start)}&to=${encodeURIComponent(end)}`;
    this.http.get<BookingResponse[]>(`${environment.apiBaseUrl}/api/bookings${params}`).subscribe({
      next: (b) => this.bookings.set(b),
      error: () => this.bookings.set([])
    });
  }

  private loadReference(): void {
    this.http.get<ServiceItem[]>(`${environment.apiBaseUrl}/api/services`).subscribe({
      next: (s) => {
        this.services.set(s);
        if (s.length > 0 && this.walkInServiceId() === 0) this.walkInServiceId.set(s[0].id);
      }
    });
    this.http.get<LineupTherapist[]>(`${environment.apiBaseUrl}/api/decking/lineup`).subscribe({
      next: (l) => this.lineup.set(l)
    });
    this.http.get<RoomItem[]>(`${environment.apiBaseUrl}/api/rooms`).subscribe({
      next: (r) => this.rooms.set(r)
    });
  }

  formatTime(iso: string | null): string {
    if (!iso) return '-';
    return new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }

  serviceName(id: number): string {
    return this.services().find(s => s.id === id)?.name ?? '-';
  }

  bedClass(room: RoomItem, bed: BedItem): string {
    const base = 'rounded border text-xs px-2 py-1 ';
    if (this.startBedId() === bed.id) return base + 'bg-[var(--sumi-primary)] text-white border-transparent';
    const status = bed.occupancy['status'];
    if (status === 'OCCUPIED') return base + 'bg-slate-200 text-slate-500 cursor-not-allowed';
    return base + 'bg-white hover:bg-slate-100';
  }

  walkInBedClass(bed: BedItem): string {
    const base = 'rounded border text-xs px-2 py-1 ';
    if (this.walkInBedId === bed.id) return base + 'bg-[var(--sumi-primary)] text-white border-transparent';
    const status = bed.occupancy['status'];
    if (status === 'OCCUPIED') return base + 'bg-slate-200 text-slate-500 cursor-not-allowed';
    return base + 'bg-white hover:bg-slate-100';
  }

  pickWalkInBed(bed: BedItem): void {
    if (bed.occupancy['status'] === 'OCCUPIED') return;
    this.walkInBedId = bed.id;
  }

  pickBed(room: RoomItem, bed: BedItem): void {
    if (bed.occupancy['status'] === 'OCCUPIED') return;
    this.startRoomId.set(room.id);
    this.startBedId.set(bed.id);
  }

  openStart(b: BookingResponse): void {
    this.startBooking.set(b);
    this.startPrimaryTherapistId.set(null);
    this.startSecondaryTherapistId.set(null);
    this.startRoomId.set(null);
    this.startBedId.set(null);
    this.startSpecificallyRequested.set(false);
  }

  cancelStart(): void {
    this.startBooking.set(null);
  }

  submitStart(): void {
    const booking = this.startBooking();
    if (!booking || !this.canStart()) return;
    const payload = {
      primaryTherapistId: this.startPrimaryTherapistId(),
      secondaryTherapistId: this.startSecondaryTherapistId(),
      roomId: this.startRoomId(),
      bedId: this.startBedId(),
      specificallyRequested: this.startSpecificallyRequested()
    };
    this.http.post<SessionResponse>(`${environment.apiBaseUrl}/api/bookings/${booking.id}/sessions`, payload).subscribe({
      next: () => {
        this.startBooking.set(null);
        this.reload();
      }
    });
  }

  endSession(b: BookingResponse): void {
    this.lookupSession(b.id).subscribe(session => {
      if (!session) return;
      this.http.post(`${environment.apiBaseUrl}/api/sessions/${session.id}/end`, {}).subscribe({
        next: () => this.reload()
      });
    });
  }

  extendSession(b: BookingResponse): void {
    this.lookupSession(b.id).subscribe(session => {
      if (!session) return;
      this.http.post(`${environment.apiBaseUrl}/api/sessions/${session.id}/extend?minutes=30`, {}).subscribe({
        next: () => this.reload()
      });
    });
  }

  adjustSession(b: BookingResponse): void {
    const newStart = window.prompt('New start time (ISO, leave blank to skip):', '');
    const newEnd = window.prompt('New end time (ISO, leave blank to skip):', '');
    this.lookupSession(b.id).subscribe(session => {
      if (!session) return;
      const params: string[] = [];
      if (newStart) params.push(`startAt=${encodeURIComponent(newStart)}`);
      if (newEnd) params.push(`endAt=${encodeURIComponent(newEnd)}`);
      const qs = params.length ? `?${params.join('&')}` : '';
      this.http.post(`${environment.apiBaseUrl}/api/sessions/${session.id}/adjust-times${qs}`, {}).subscribe({
        next: () => this.reload()
      });
    });
  }

  generateSlip(b: BookingResponse): void {
    this.lookupSession(b.id).subscribe(session => {
      if (!session) return;
      this.http.post<{ id: string }>(`${environment.apiBaseUrl}/api/treatment-slips/from-session/${session.id}`, {}).subscribe({
        next: (slip) => this.router.navigate(['/app/treatment-slips', slip.id])
      });
    });
  }

  openWalkIn(): void {
    this.walkInError.set(null);
    this.walkInSubmitting.set(false);
    this.walkInNickname = '';
    this.walkInLocker = '';
    this.walkInPax = 1;
    this.walkInReservationType = 'WALK_IN';
    this.walkInStartTime = this.nowDatetimeLocal();
    this.walkInEndTimeOverride = '';
    this.walkInPrimaryTherapistId = null;
    this.walkInSecondaryTherapistId = null;
    this.walkInRoomId.set(null);
    this.walkInBedId = null;
    this.walkInSpecificallyRequested = false;
    this.walkInJacuzziMinutes = 60;
    this.walkInMassageMinutes = 60;
    this.walkInWine = false;
    this.walkInOrNumber = '';
    this.walkInAddOnOrNumber = '';
    this.walkInOthersAddOn = '';
    this.walkInRemarks = '';
    this.walkInTotalAmount = null;
    this.walkInWaiver = false;
    this.showWalkIn.set(true);
    this.loadReference();
  }

  onWalkInRoomChange(value: string): void {
    this.walkInRoomId.set(value || null);
    this.walkInBedId = null;
  }

  submitWalkIn(): void {
    if (this.walkInSubmitting()) return;
    if (!this.walkInWaiver) {
      this.walkInError.set('Client must accept the waiver before proceeding.');
      return;
    }
    if (!this.walkInNickname || !this.walkInServiceId()) {
      this.walkInError.set('Customer name and treatment are required.');
      return;
    }
    this.walkInError.set(null);
    this.walkInSubmitting.set(true);

    const isVip = this.isWalkInVip();
    const endTimeStr = this.walkInEndTimeOverride || this.walkInComputedEnd();
    const payload = {
      clientNickname: this.walkInNickname,
      serviceId: Number(this.walkInServiceId()),
      reservationType: this.walkInReservationType,
      pax: isVip ? null : (Number(this.walkInPax) || 1),
      lockerNumber: this.walkInLocker || null,
      startTime: new Date(this.walkInStartTime).toISOString(),
      endTime: endTimeStr ? new Date(endTimeStr).toISOString() : null,
      primaryTherapistId: this.walkInPrimaryTherapistId,
      secondaryTherapistId: this.walkInSecondaryTherapistId,
      roomId: this.walkInRoomId(),
      bedId: this.walkInBedId,
      specificallyRequested: this.walkInSpecificallyRequested,
      jacuzziMinutes: isVip ? (Number(this.walkInJacuzziMinutes) || null) : null,
      massageMinutes: isVip ? (Number(this.walkInMassageMinutes) || null) : null,
      wineIncluded: isVip ? this.walkInWine : null,
      orNumber: this.walkInOrNumber || null,
      addOnOrNumber: this.walkInAddOnOrNumber || null,
      othersAddOn: this.walkInOthersAddOn || null,
      remarks: this.walkInRemarks || null,
      totalAmount: this.walkInTotalAmount,
      waiverAccepted: this.walkInWaiver
    };

    this.http.post<WalkInResponse>(`${environment.apiBaseUrl}/api/walk-in`, payload).subscribe({
      next: (res) => {
        this.walkInSubmitting.set(false);
        this.showWalkIn.set(false);
        this.reload();
        this.router.navigate(['/app/treatment-slips', res.slipId]);
      },
      error: (err) => {
        this.walkInSubmitting.set(false);
        this.walkInError.set(err?.error?.message ?? 'Failed to create walk-in. Please review the form and try again.');
      }
    });
  }

  private nowDatetimeLocal(): string {
    return this.toDatetimeLocal(new Date());
  }

  private toDatetimeLocal(d: Date): string {
    const local = new Date(d.getTime() - d.getTimezoneOffset() * 60_000);
    local.setSeconds(0, 0);
    return local.toISOString().slice(0, 16);
  }

  private lookupSession(bookingId: string) {
    return this.http.get<SessionResponse>(`${environment.apiBaseUrl}/api/sessions/by-booking/${bookingId}`);
  }
}
