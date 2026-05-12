import { ChangeDetectionStrategy, Component, OnInit, OnDestroy, computed, inject, signal } from '@angular/core';
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
  clientGender?: string | null;
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
  onCall: boolean;
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

interface OrderStatus {
  id: string;
  status: string;
}

@Component({
  selector: 'sumi-bookings',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './bookings.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class BookingsComponent implements OnInit, OnDestroy {
  private http = inject(HttpClient);
  private router = inject(Router);
  private therapistRefreshTimer: any;

  selectedDate = signal(new Date().toISOString().slice(0, 10));
  bookings = signal<BookingResponse[]>([]);
  services = signal<ServiceItem[]>([]);
  lineup = signal<LineupTherapist[]>([]);
  rooms = signal<RoomItem[]>([]);
  orderStatuses = signal<Map<string, string>>(new Map());

  startBooking = signal<BookingResponse | null>(null);
  startPrimaryTherapistId = signal<string | null>(null);
  startSecondaryTherapistId = signal<string | null>(null);
  startRoomId = signal<string | null>(null);
  startBedId = signal<string | null>(null);
  startSpecificallyRequested = signal(false);

  editBooking = signal<BookingResponse | null>(null);
  editServiceId = signal<number>(0);
  editLockerNumber = '';
  editClientNickname = '';
  editError = signal<string | null>(null);

  adjustBooking = signal<BookingResponse | null>(null);
  adjustNewStart = '';
  adjustNewEnd = '';

  availableTherapists = computed(() => {
    return this.lineup().filter(t => !t.onCall);
  });

  selectedStartService = computed(() => {
    const id = this.startBooking()?.serviceId;
    if (!id) return null;
    return this.services().find(s => s.id === id) ?? null;
  });

  needsSecondary = computed(() => {
    return this.selectedStartService()?.requiresTwoTherapists ?? false;
  });

  pickedRoomNumber = computed(() => {
    const room = this.rooms().find(r => r.id === this.startRoomId());
    return room?.roomNumber ?? '';
  });

  pickedBedLabel = computed(() => {
    const room = this.rooms().find(r => r.id === this.startRoomId());
    return room?.beds.find(b => b.id === this.startBedId())?.label ?? '';
  });

  canStart = computed(() =>
    this.startPrimaryTherapistId() !== null &&
    this.startRoomId() !== null &&
    this.startBedId() !== null
  );

  ngOnInit(): void {
    this.reload();
    this.loadReference();
    this.therapistRefreshTimer = setInterval(() => this.refreshLineup(), 30000);
  }

  ngOnDestroy(): void {
    if (this.therapistRefreshTimer) {
      clearInterval(this.therapistRefreshTimer);
    }
  }

  onDateChange(value: string): void {
    this.selectedDate.set(value);
    this.reload();
  }

  reload(): void {
    const d = this.selectedDate();
    const start = `${d}T00:00:00.000+08:00`;
    const end = `${d}T23:59:59.999+08:00`;
    const params = `?from=${encodeURIComponent(start)}&to=${encodeURIComponent(end)}`;
    this.http.get<BookingResponse[]>(`${environment.apiBaseUrl}/api/bookings${params}`).subscribe({
      next: (b) => {
        this.bookings.set(b);
        this.loadOrderStatuses(b);
      },
      error: () => this.bookings.set([])
    });
  }

  private loadOrderStatuses(bookings: BookingResponse[]): void {
    if (bookings.length === 0) return;
    this.http.get<any[]>(`${environment.apiBaseUrl}/api/cashier/orders`).subscribe({
      next: (orders) => {
        const map = new Map<string, string>();
        for (const order of orders) {
          if (order.bookingId) {
            map.set(order.bookingId, order.status);
          }
        }
        this.orderStatuses.set(map);
      }
    });
  }

  getOrderStatus(bookingId: string): string {
    const booking = this.bookings().find(b => b.id === bookingId);
    if (booking && booking.status === 'CANCELLED') return 'CANCELLED';
    return this.orderStatuses().get(bookingId) ?? 'PENDING';
  }

  isOrderPaid(bookingId: string): boolean {
    const status = this.getOrderStatus(bookingId);
    return status === 'PAID';
  }

  private loadReference(): void {
    this.http.get<ServiceItem[]>(`${environment.apiBaseUrl}/api/services`).subscribe({
      next: (s) => this.services.set(s)
    });
    this.refreshLineup();
    this.http.get<RoomItem[]>(`${environment.apiBaseUrl}/api/rooms`).subscribe({
      next: (r) => this.rooms.set(r)
    });
  }

  private refreshLineup(): void {
    this.http.get<LineupTherapist[]>(`${environment.apiBaseUrl}/api/decking/lineup`).subscribe({
      next: (l) => this.lineup.set(l)
    });
  }

  formatTime(iso: string | null): string {
    if (!iso) return '-';
    return new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }

  serviceName(id: number): string {
    return this.services().find(s => s.id === id)?.name ?? '-';
  }

  isBedSelectableForGender(bed: BedItem, clientGender: string | null | undefined): boolean {
    if (bed.occupancy['status'] === 'OCCUPIED') {
      const lock = bed.occupancy['genderLock'];
      if (lock && clientGender && lock !== clientGender) {
        return false; // Opposing gender occupying room
      }
      return false; // Occupied
    }
    return true;
  }

  bedClass(room: RoomItem, bed: BedItem): string {
    const base = 'rounded border text-xs px-2 py-1 ';
    if (this.startBedId() === bed.id) return base + 'bg-[var(--sumi-primary)] text-white border-transparent';
    const clientGender = this.startBooking()?.clientGender;
    if (!this.isBedSelectableForGender(bed, clientGender)) {
      const lock = bed.occupancy['genderLock'];
      if (lock === 'M') return base + 'bg-blue-100 text-blue-500 cursor-not-allowed';
      if (lock === 'F') return base + 'bg-pink-100 text-pink-500 cursor-not-allowed';
      return base + 'bg-slate-200 text-slate-500 cursor-not-allowed';
    }
    return base + 'bg-white hover:bg-slate-100';
  }

  pickBed(room: RoomItem, bed: BedItem): void {
    const clientGender = this.startBooking()?.clientGender;
    if (!this.isBedSelectableForGender(bed, clientGender)) return;
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
    this.refreshLineup();
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
      },
      error: (err) => {
        alert(err?.error?.message || 'Could not start session.');
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

  openAdjust(b: BookingResponse): void {
    this.adjustBooking.set(b);
    this.adjustNewStart = '';
    this.adjustNewEnd = '';
  }

  cancelAdjust(): void {
    this.adjustBooking.set(null);
  }

  submitAdjust(): void {
    const b = this.adjustBooking();
    if (!b) return;
    this.lookupSession(b.id).subscribe(session => {
      if (!session) return;
      const params: string[] = [];
      if (this.adjustNewStart) params.push(`startAt=${encodeURIComponent(new Date(this.adjustNewStart).toISOString())}`);
      if (this.adjustNewEnd) params.push(`endAt=${encodeURIComponent(new Date(this.adjustNewEnd).toISOString())}`);
      const qs = params.length ? `?${params.join('&')}` : '';
      this.http.post(`${environment.apiBaseUrl}/api/sessions/${session.id}/adjust-times${qs}`, {}).subscribe({
        next: () => {
          this.adjustBooking.set(null);
          this.reload();
        }
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

  openEdit(b: BookingResponse): void {
    this.editBooking.set(b);
    this.editServiceId.set(b.serviceId);
    this.editLockerNumber = b.lockerNumber || '';
    this.editClientNickname = b.clientNickname;
    this.editError.set(null);
  }

  cancelEdit(): void {
    this.editBooking.set(null);
  }

  submitEdit(): void {
    const b = this.editBooking();
    if (!b) return;
    this.http.patch(`${environment.apiBaseUrl}/api/bookings/${b.id}`, {
      serviceId: Number(this.editServiceId()),
      lockerNumber: this.editLockerNumber || null,
      clientNickname: this.editClientNickname || null
    }).subscribe({
      next: () => {
        this.editBooking.set(null);
        this.reload();
      },
      error: (err) => this.editError.set(err?.error?.message || 'Could not update booking.')
    });
  }

  exportCsv(): void {
    const d = this.selectedDate();
    const from = encodeURIComponent(`${d}T00:00:00.000+08:00`);
    const to = encodeURIComponent(`${d}T23:59:59.999+08:00`);
    this.http.get(`${environment.apiBaseUrl}/api/bookings/export.csv?from=${from}&to=${to}`,
      { responseType: 'blob' as const, observe: 'response' as const }
    ).subscribe({
      next: (response) => {
        const blob = response.body;
        if (!blob) { alert('Export returned no data.'); return; }
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `bookings-${d}.csv`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
      },
      error: (err) => {
        const status = err?.status ?? 0;
        if (status === 401 || status === 403) {
          alert('You do not have permission to export bookings, or your session has expired.');
        } else {
          alert('Export failed (status ' + status + '). Please try again.');
        }
      }
    });
  }

  private lookupSession(bookingId: string) {
    return this.http.get<SessionResponse>(`${environment.apiBaseUrl}/api/sessions/by-booking/${bookingId}`);
  }
}
