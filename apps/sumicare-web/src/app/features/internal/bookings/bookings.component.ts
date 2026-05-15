import { ChangeDetectionStrategy, Component, OnInit, OnDestroy, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { ConfirmService } from '../../../shared/components/confirm-dialog/confirm.service';

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
  orderId?: string | null;
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

interface OrderAttendee {
  id: string;
  serviceName: string | null;
  lockerNumber: string | null;
  clientGender: string | null;
  sessionId: string | null;
  sessionStatus: string | null;
  treatmentSlipId: string | null;
}

interface OrderItemLite {
  id: string;
  packageName: string;
  unitPrice: number;
  attendees: OrderAttendee[];
}

interface OrderLite {
  id: string;
  bookingId: string | null;
  status: string;
  roomType: string | null;
  groupBooking: boolean;
  couplePackage: boolean;
  items: OrderItemLite[];
}

@Component({
  selector: 'sumi-bookings',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './bookings.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class BookingsComponent implements OnInit, OnDestroy {
  private http = inject(HttpClient);
  private router = inject(Router);
  private confirmService = inject(ConfirmService);
  private therapistRefreshTimer: any;

  selectedDate = signal(new Date().toISOString().slice(0, 10));
  bookings = signal<BookingResponse[]>([]);
  services = signal<ServiceItem[]>([]);
  lineup = signal<LineupTherapist[]>([]);
  rooms = signal<RoomItem[]>([]);
  orderStatuses = signal<Map<string, string>>(new Map());
  ordersByBooking = signal<Map<string, OrderLite>>(new Map());
  expandedBookingId = signal<string | null>(null);

  startBooking = signal<BookingResponse | null>(null);
  startAttendeeId = signal<string | null>(null);
  startAttendeeGender = signal<string | null>(null);
  startAttendeeLabel = signal<string>('');
  startPrimaryTherapistId = signal<string | null>(null);
  startSecondaryTherapistId = signal<string | null>(null);
  startRoomId = signal<string | null>(null);
  startBedId = signal<string | null>(null);
  startSpecificallyRequested = signal(false);
  startOrderRoomType = signal<string | null>(null);

  editBooking = signal<BookingResponse | null>(null);
  editServiceId = signal<number>(0);
  editLockerNumber = '';
  editClientNickname = '';
  editError = signal<string | null>(null);

  adjustBooking = signal<BookingResponse | null>(null);
  adjustSessionId = signal<string | null>(null);
  adjustAttendeeLabel = signal<string>('');
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

  isCouplePackage = computed(() => {
    const booking = this.startBooking();
    if (!booking) return false;
    const ord = this.ordersByBooking().get(booking.id);
    return ord?.couplePackage ?? false;
  });

  filteredStartRooms = computed(() => {
    const rt = this.startOrderRoomType();
    const all = this.rooms();
    if (!rt) return all;
    const want = rt.toUpperCase();
    return all.filter(r => {
      const t = (r.roomType || '').toUpperCase();
      if (want === 'VIP') return t.includes('VIP');
      if (want === 'PRIVATE') return t.includes('PRIVATE');
      return !t.includes('VIP') && !t.includes('PRIVATE');
    });
  });

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
    this.http.get<OrderLite[]>(`${environment.apiBaseUrl}/api/cashier/orders`).subscribe({
      next: (orders) => {
        const statusMap = new Map<string, string>();
        const orderMap = new Map<string, OrderLite>();
        for (const order of orders) {
          if (order.bookingId) {
            statusMap.set(order.bookingId, order.status);
            orderMap.set(order.bookingId, order);
          }
        }
        this.orderStatuses.set(statusMap);
        this.ordersByBooking.set(orderMap);
      }
    });
  }

  orderForBooking(bookingId: string): OrderLite | null {
    return this.ordersByBooking().get(bookingId) ?? null;
  }

  attendeeCount(bookingId: string): number {
    const order = this.ordersByBooking().get(bookingId);
    if (!order || !order.items) return 0;
    return order.items.reduce((sum, it) => sum + (it.attendees ? it.attendees.length : 0), 0);
  }

  toggleExpand(bookingId: string): void {
    this.expandedBookingId.set(this.expandedBookingId() === bookingId ? null : bookingId);
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
    return new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false, hourCycle: 'h23' });
  }

  serviceName(id: number): string {
    return this.services().find(s => s.id === id)?.name ?? '-';
  }

  activeStartGender(): string | null {
    return this.startAttendeeGender() ?? this.startBooking()?.clientGender ?? null;
  }

  isBedSelectableForGender(bed: BedItem, clientGender: string | null | undefined): boolean {
    if (bed.occupancy['status'] === 'OCCUPIED') {
      return false;
    }
    return true;
  }

  isRoomSelectableForGender(room: RoomItem, clientGender: string | null | undefined): boolean {
    if ((room.roomType || '').toUpperCase() !== 'COMMON') return true;
    if (!clientGender) return true;
    if (this.isCouplePackage()) return true;
    return !room.beds.some(b =>
      b.occupancy['status'] === 'OCCUPIED' &&
      b.occupancy['genderLock'] &&
      b.occupancy['genderLock'] !== clientGender
    );
  }

  roomCardClass(room: RoomItem): string {
    const base = 'border rounded p-2 ';
    if (!this.isRoomSelectableForGender(room, this.activeStartGender())) {
      return base + 'opacity-50 bg-slate-100';
    }
    return base;
  }

  bedClass(room: RoomItem, bed: BedItem): string {
    const base = 'rounded border text-xs px-2 py-1 ';
    if (this.startBedId() === bed.id) return base + 'bg-[var(--sumi-primary)] text-white border-transparent';
    const clientGender = this.activeStartGender();
    if (!this.isRoomSelectableForGender(room, clientGender)) {
      return base + 'bg-slate-200 text-slate-400 cursor-not-allowed';
    }
    if (!this.isBedSelectableForGender(bed, clientGender)) {
      const lock = bed.occupancy['genderLock'];
      if (lock === 'M') return base + 'bg-blue-100 text-blue-500 cursor-not-allowed';
      if (lock === 'F') return base + 'bg-pink-100 text-pink-500 cursor-not-allowed';
      return base + 'bg-slate-200 text-slate-500 cursor-not-allowed';
    }
    return base + 'bg-white hover:bg-slate-100';
  }

  pickBed(room: RoomItem, bed: BedItem): void {
    const clientGender = this.activeStartGender();
    if (!this.isRoomSelectableForGender(room, clientGender)) return;
    if (!this.isBedSelectableForGender(bed, clientGender)) return;
    this.startRoomId.set(room.id);
    this.startBedId.set(bed.id);
  }

  private resetStartForm(b: BookingResponse): void {
    this.startBooking.set(b);
    this.startPrimaryTherapistId.set(null);
    this.startSecondaryTherapistId.set(null);
    this.startRoomId.set(null);
    this.startBedId.set(null);
    this.startSpecificallyRequested.set(false);
    const order = this.ordersByBooking().get(b.id);
    this.startOrderRoomType.set(order ? (order.roomType ?? null) : null);
    this.http.get<OrderLite>(`${environment.apiBaseUrl}/api/cashier/orders/by-booking/${b.id}`).subscribe({
      next: (o) => this.startOrderRoomType.set(o.roomType ?? null),
      error: () => { /* no order yet — show all rooms */ }
    });
    this.refreshLineup();
  }

  openStart(b: BookingResponse): void {
    this.resetStartForm(b);
    const order = this.ordersByBooking().get(b.id);
    const attendees = order?.items?.flatMap(it => it.attendees) ?? [];
    if (attendees.length === 1) {
      this.startAttendeeId.set(attendees[0].id);
      this.startAttendeeGender.set(attendees[0].clientGender ?? b.clientGender ?? null);
      this.startAttendeeLabel.set(attendees[0].serviceName || b.clientNickname);
    } else {
      this.startAttendeeId.set(null);
      this.startAttendeeGender.set(b.clientGender ?? null);
      this.startAttendeeLabel.set('');
    }
  }

  openStartForAttendee(b: BookingResponse, attendee: OrderAttendee): void {
    this.resetStartForm(b);
    this.startAttendeeId.set(attendee.id);
    this.startAttendeeGender.set(attendee.clientGender ?? b.clientGender ?? null);
    this.startAttendeeLabel.set(attendee.serviceName || ('Guest · locker ' + (attendee.lockerNumber || '—')));
  }

  cancelStart(): void {
    this.startBooking.set(null);
    this.startAttendeeId.set(null);
    this.startAttendeeGender.set(null);
    this.startAttendeeLabel.set('');
  }

  async submitStart(): Promise<void> {
    const booking = this.startBooking();
    if (!booking || !this.canStart()) return;
    
    const confirmed = await this.confirmService.confirm({
      title: 'Start Session',
      message: 'Are you sure you want to start this session?',
      confirmText: 'Start Session'
    });
    if (!confirmed) return;
    
    const payload = {
      primaryTherapistId: this.startPrimaryTherapistId(),
      secondaryTherapistId: this.startSecondaryTherapistId(),
      roomId: this.startRoomId(),
      bedId: this.startBedId(),
      specificallyRequested: this.startSpecificallyRequested()
    };
    const attendeeId = this.startAttendeeId();
    const url = attendeeId
      ? `${environment.apiBaseUrl}/api/bookings/attendees/${attendeeId}/sessions`
      : `${environment.apiBaseUrl}/api/bookings/${booking.id}/sessions`;
    this.http.post<SessionResponse>(url, payload).subscribe({
      next: () => {
        this.cancelStart();
        this.reload();
      },
      error: (err) => {
        alert(err?.error?.message || 'Could not start session.');
      }
    });
  }

  async endAttendeeSession(attendee: OrderAttendee): Promise<void> {
    if (!attendee.sessionId) return;
    const confirmed = await this.confirmService.confirm({
      title: 'End Sub-session',
      message: 'Are you sure you want to end this sub-session?',
      confirmText: 'End Session',
      danger: true
    });
    if (!confirmed) return;
    this.http.post(`${environment.apiBaseUrl}/api/sessions/${attendee.sessionId}/end`, {}).subscribe({
      next: () => this.reload()
    });
  }

  async endSession(b: BookingResponse): Promise<void> {
    const confirmed = await this.confirmService.confirm({
      title: 'End Session',
      message: 'Are you sure you want to end this session?',
      confirmText: 'End Session',
      danger: true
    });
    if (!confirmed) return;
    
    this.lookupSession(b.id).subscribe(session => {
      if (!session) return;
      this.http.post(`${environment.apiBaseUrl}/api/sessions/${session.id}/end`, {}).subscribe({
        next: () => this.reload()
      });
    });
  }

  async extendAttendeeSession(attendee: OrderAttendee): Promise<void> {
    if (!attendee.sessionId) return;
    const confirmed = await this.confirmService.confirm({
      title: 'Extend Sub-session',
      message: 'Do you want to extend this sub-session by 30 minutes?',
      confirmText: 'Extend'
    });
    if (!confirmed) return;
    this.http.post(`${environment.apiBaseUrl}/api/sessions/${attendee.sessionId}/extend?minutes=30`, {}).subscribe({
      next: () => this.reload()
    });
  }

  async extendSession(b: BookingResponse): Promise<void> {
    const confirmed = await this.confirmService.confirm({
      title: 'Extend Session',
      message: 'Do you want to extend this session by 30 minutes?',
      confirmText: 'Extend'
    });
    if (!confirmed) return;
    
    this.lookupSession(b.id).subscribe(session => {
      if (!session) return;
      this.http.post(`${environment.apiBaseUrl}/api/sessions/${session.id}/extend?minutes=30`, {}).subscribe({
        next: () => this.reload()
      });
    });
  }

  openAdjust(b: BookingResponse, sessionId?: string, label?: string): void {
    this.adjustBooking.set(b);
    this.adjustSessionId.set(sessionId ?? null);
    this.adjustAttendeeLabel.set(label ?? '');
    this.adjustNewStart = '';
    this.adjustNewEnd = '';
  }

  cancelAdjust(): void {
    this.adjustBooking.set(null);
    this.adjustSessionId.set(null);
    this.adjustAttendeeLabel.set('');
  }

  async submitAdjust(): Promise<void> {
    const b = this.adjustBooking();
    if (!b) return;
    const confirmed = await this.confirmService.confirm({
      title: 'Adjust Session Times',
      message: 'Are you sure you want to adjust the session times?',
      confirmText: 'Apply'
    });
    if (!confirmed) return;
    const sid = this.adjustSessionId();
    const lookup$ = sid
      ? this.http.get<SessionResponse>(`${environment.apiBaseUrl}/api/sessions/by-id/${sid}`)
      : this.lookupSession(b.id);
    lookup$.subscribe(session => {
      if (!session) return;
      const params: string[] = [];
      if (this.adjustNewStart) params.push(`startAt=${encodeURIComponent(new Date(this.adjustNewStart).toISOString())}`);
      if (this.adjustNewEnd) params.push(`endAt=${encodeURIComponent(new Date(this.adjustNewEnd).toISOString())}`);
      const qs = params.length ? `?${params.join('&')}` : '';
      this.http.post(`${environment.apiBaseUrl}/api/sessions/${session.id}/adjust-times${qs}`, {}).subscribe({
        next: () => {
          this.adjustBooking.set(null);
          this.adjustSessionId.set(null);
          this.adjustAttendeeLabel.set('');
          this.reload();
        }
      });
    });
  }

  async generateSlip(b: BookingResponse): Promise<void> {
    const confirmed = await this.confirmService.confirm({
      title: 'Generate Treatment Slip',
      message: 'Generate a treatment slip for this session?',
      confirmText: 'Generate'
    });
    if (!confirmed) return;
    this.lookupSession(b.id).subscribe(session => {
      if (!session) return;
      this.http.post<{ id: string }>(`${environment.apiBaseUrl}/api/treatment-slips/from-session/${session.id}`, {}).subscribe({
        next: (slip) => this.router.navigate(['/app/treatment-slips', slip.id])
      });
    });
  }

  async generateSlipForSession(sessionId: string): Promise<void> {
    const confirmed = await this.confirmService.confirm({
      title: 'Generate Treatment Slip',
      message: 'Generate a treatment slip for this sub-session?',
      confirmText: 'Generate'
    });
    if (!confirmed) return;
    this.http.post<{ id: string }>(`${environment.apiBaseUrl}/api/treatment-slips/from-session/${sessionId}`, {}).subscribe({
      next: (slip) => this.router.navigate(['/app/treatment-slips', slip.id])
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
