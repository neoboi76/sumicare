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

interface TherapistItem {
  id: string;
  nickname: string;
  gender: string;
  backup: boolean;
  active: boolean;
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
  therapists = signal<TherapistItem[]>([]);
  rooms = signal<RoomItem[]>([]);
  showWalkIn = signal(false);
  startBooking = signal<BookingResponse | null>(null);

  walkInNickname = '';
  walkInLocker = '';
  walkInServiceId = 0;
  walkInReservationType = 'HARD';
  walkInScheduledAt = this.nowDatetimeLocal();

  startPrimaryTherapistId = signal<string | null>(null);
  startSecondaryTherapistId = signal<string | null>(null);
  startRoomId = signal<string | null>(null);
  startBedId = signal<string | null>(null);
  startSpecificallyRequested = signal(false);

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
        if (s.length > 0 && this.walkInServiceId === 0) this.walkInServiceId = s[0].id;
      }
    });
    this.http.get<TherapistItem[]>(`${environment.apiBaseUrl}/api/therapists`).subscribe({
      next: (t) => this.therapists.set(t.filter(x => x.active && !x.backup))
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

  submitWalkIn(): void {
    const payload = {
      clientNickname: this.walkInNickname,
      lockerNumber: this.walkInLocker || undefined,
      serviceId: Number(this.walkInServiceId),
      reservationType: this.walkInReservationType,
      scheduledAt: new Date(this.walkInScheduledAt).toISOString()
    };
    this.http.post(`${environment.apiBaseUrl}/api/bookings`, payload).subscribe({
      next: () => {
        this.showWalkIn.set(false);
        this.walkInNickname = '';
        this.walkInLocker = '';
        this.reload();
      }
    });
  }

  private nowDatetimeLocal(): string {
    const d = new Date();
    d.setSeconds(0, 0);
    return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
  }

  private lookupSession(bookingId: string) {
    return this.http.get<SessionResponse>(`${environment.apiBaseUrl}/api/sessions/by-booking/${bookingId}`);
  }
}
