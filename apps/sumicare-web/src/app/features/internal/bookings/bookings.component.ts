import { ChangeDetectionStrategy, Component, OnInit, OnDestroy, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { ConfirmService } from '../../../shared/components/confirm-dialog/confirm.service';
import { SortableColumnDirective } from '../../../shared/directives/sortable-column.directive';
import { SortIconComponent } from '../../../shared/components/sort-icon/sort-icon.component';
import { SortState, sortRows } from '../../../shared/utils/compare-by';

interface BookingResponse {
  id: string;
  clientNickname: string;
  lockerNumber: string | null;
  serviceId: number;
  reservationType: string;
  scheduledAt: string;
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
  fixedRate: boolean;
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
  serviceId: number | null;
  serviceName: string | null;
  lockerNumber: string | null;
  clientGender: string | null;
  sessionId: string | null;
  sessionStatus: string | null;
  treatmentSlipId: string | null;
}

interface OrderItemLite {
  id: string;
  packageId: number | null;
  packageName: string;
  unitPrice: number;
  roomType: string | null;
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
  imports: [FormsModule, RouterLink, SortableColumnDirective, SortIconComponent],
  templateUrl: './bookings.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class BookingsComponent implements OnInit, OnDestroy {
  private http = inject(HttpClient);
  private router = inject(Router);
  private confirmService = inject(ConfirmService);
  private therapistRefreshTimer: any;

  selectedDate = signal(new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Manila' }).format(new Date()));
  bookings = signal<BookingResponse[]>([]);
  services = signal<ServiceItem[]>([]);
  lineup = signal<LineupTherapist[]>([]);
  rooms = signal<RoomItem[]>([]);
  orderStatuses = signal<Map<string, string>>(new Map());
  ordersByBooking = signal<Map<string, OrderLite>>(new Map());
  expandedBookingId = signal<string | null>(null);

  startBooking = signal<BookingResponse | null>(null);
  startAttendeeId = signal<string | null>(null);
  startAttendeeServiceId = signal<number | null>(null);
  startAttendeeGender = signal<string | null>(null);
  startAttendeeLabel = signal<string>('');
  startPrimaryTherapistId = signal<string | null>(null);
  startSecondaryTherapistId = signal<string | null>(null);
  startRoomId = signal<string | null>(null);
  startBedId = signal<string | null>(null);
  startSpecificallyRequested = signal(false);
  startRoomType = signal<string | null>(null);

  editBooking = signal<BookingResponse | null>(null);
  editServiceId = signal<number>(0);
  editLockerNumber = '';
  editClientNickname = '';
  editError = signal<string | null>(null);
  extendError = signal<string | null>(null);

  sortState = signal<SortState>({ key: 'scheduledAt', direction: 'asc' });

  sortedBookings = computed(() => {
    const rows = this.bookings();
    const state = this.sortState();
    return sortRows(rows, state, (b) => {
      switch (state.key) {
        case 'scheduledAt': return b.scheduledAt;
        case 'clientNickname': return b.clientNickname;
        case 'lockerNumber': return b.lockerNumber ?? '';
        case 'serviceName': return this.serviceName(b.serviceId);
        case 'reservationType': return b.reservationType;
        case 'status': return b.status;
        case 'paymentStatus': return this.getOrderStatus(b.id);
        default: return '';
      }
    });
  });

  availableTherapists = computed(() => {
    return this.lineup().filter(t => !t.onCall && !t.skipped);
  });

  onBreakTherapists = computed(() => this.lineup().filter(t => t.skipped));

  vipPackageIds = signal<Set<number>>(new Set());

  isVipBooking(b: BookingResponse): boolean {
    const order = this.ordersByBooking().get(b.id);
    if (!order || !order.items) return false;
    const vip = this.vipPackageIds();
    return order.items.some(it => it.packageId != null && vip.has(it.packageId));
  }

  isVipAttendee(_a: OrderAttendee, b: BookingResponse): boolean {
    return this.isVipBooking(b);
  }

  private isFixedService(serviceId: number | null): boolean {
    if (serviceId == null) return false;
    return this.services().find(s => s.id === serviceId)?.fixedRate ?? false;
  }

  isFixedBooking(b: BookingResponse): boolean {
    return this.isFixedService(b.serviceId);
  }

  isFixedAttendee(a: OrderAttendee, b: BookingResponse): boolean {
    return this.isFixedService(a.serviceId ?? b.serviceId);
  }

  selectedStartService = computed(() => {
    const attendeeServiceId = this.startAttendeeServiceId();
    if (attendeeServiceId != null) {
      return this.services().find(s => s.id === attendeeServiceId) ?? null;
    }
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

  startItemId = computed(() => {
    const attId = this.startAttendeeId();
    const booking = this.startBooking();
    if (!attId || !booking) return null;
    const order = this.ordersByBooking().get(booking.id);
    const item = order?.items?.find(it => it.attendees.some(a => a.id === attId));
    return item?.id ?? null;
  });

  filteredStartRooms = computed(() => {
    const rt = this.startRoomType();
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
    const params = `?from=${encodeURIComponent(d)}&to=${encodeURIComponent(d)}`;
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
    this.orderStatuses.set(new Map());
    this.ordersByBooking.set(new Map());
    for (const b of bookings) {
      if (b.orderId) {
        this.http.get<OrderLite>(`${environment.apiBaseUrl}/api/cashier/orders/${b.orderId}`).subscribe({
          next: (order) => {
            const currentStatuses = new Map(this.orderStatuses());
            const currentOrders = new Map(this.ordersByBooking());
            currentStatuses.set(b.id, order.status);
            currentOrders.set(b.id, order);
            this.orderStatuses.set(currentStatuses);
            this.ordersByBooking.set(currentOrders);
          },
          error: () => { }
        });
      }
    }
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
    const current = this.expandedBookingId();
    if (current === bookingId) {
      this.expandedBookingId.set(null);
    } else {
      this.expandedBookingId.set(bookingId);
      const booking = this.bookings().find(b => b.id === bookingId);
      if (booking?.orderId && !this.ordersByBooking().get(bookingId)) {
        this.http.get<OrderLite>(`${environment.apiBaseUrl}/api/cashier/orders/${booking.orderId}`).subscribe({
          next: (order) => {
            const currentOrders = new Map(this.ordersByBooking());
            currentOrders.set(bookingId, order);
            this.ordersByBooking.set(currentOrders);
          }
        });
      }
    }
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
    this.http.get<Array<{ id: number; requiresVipRoom: boolean }>>(`${environment.apiBaseUrl}/api/cashier/packages/all`).subscribe({
      next: (pkgs) => {
        const vipIds = new Set<number>();
        for (const p of pkgs) if (p.requiresVipRoom) vipIds.add(p.id);
        this.vipPackageIds.set(vipIds);
      },
      error: () => this.vipPackageIds.set(new Set())
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

  sourceLabel(b: BookingResponse): string {
    return b.reservationType === 'WALK_IN' ? 'Walk-in' : 'Online';
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
    const myItem = this.startItemId();
    return !room.beds.some(b =>
      b.occupancy['status'] === 'OCCUPIED' &&
      b.occupancy['genderLock'] &&
      b.occupancy['genderLock'] !== clientGender &&
      b.occupancy['ownerItemId'] !== myItem
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

  private roomTypeForAttendee(order: OrderLite | null | undefined, attendeeId: string | null): string | null {
    if (!order) return null;
    if (attendeeId) {
      const item = order.items?.find(it => it.attendees.some(a => a.id === attendeeId));
      if (item) return item.roomType ?? null;
    }
    return order.roomType ?? null;
  }

  private resetStartForm(b: BookingResponse, attendeeId: string | null): void {
    this.startBooking.set(b);
    this.startPrimaryTherapistId.set(null);
    this.startSecondaryTherapistId.set(null);
    this.startRoomId.set(null);
    this.startBedId.set(null);
    this.startSpecificallyRequested.set(false);
    const cached = this.ordersByBooking().get(b.id);
    this.startRoomType.set(this.roomTypeForAttendee(cached, attendeeId));
    this.http.get<OrderLite>(`${environment.apiBaseUrl}/api/cashier/orders/by-booking/${b.id}`).subscribe({
      next: (o) => this.startRoomType.set(this.roomTypeForAttendee(o, attendeeId)),
      error: () => { }
    });
    this.refreshLineup();
  }

  openStart(b: BookingResponse): void {
    const order = this.ordersByBooking().get(b.id);
    const attendees = order?.items?.flatMap(it => it.attendees) ?? [];
    const single = attendees.length === 1 ? attendees[0] : null;
    this.resetStartForm(b, single?.id ?? null);
    if (single) {
      this.startAttendeeId.set(single.id);
      this.startAttendeeServiceId.set(single.serviceId ?? null);
      this.startAttendeeGender.set(single.clientGender ?? b.clientGender ?? null);
      this.startAttendeeLabel.set(single.serviceName || b.clientNickname);
    } else {
      this.startAttendeeId.set(null);
      this.startAttendeeServiceId.set(null);
      this.startAttendeeGender.set(b.clientGender ?? null);
      this.startAttendeeLabel.set('');
    }
  }

  openStartForAttendee(b: BookingResponse, attendee: OrderAttendee): void {
    this.resetStartForm(b, attendee.id);
    this.startAttendeeId.set(attendee.id);
    this.startAttendeeServiceId.set(attendee.serviceId ?? null);
    this.startAttendeeGender.set(attendee.clientGender ?? b.clientGender ?? null);
    const svc = attendee.serviceId ? this.services().find(s => s.id === attendee.serviceId) : null;
    const tandemSuffix = svc?.requiresTwoTherapists ? ' · TANDEM' : '';
    this.startAttendeeLabel.set((attendee.serviceName || ('Guest · locker ' + (attendee.lockerNumber || '—'))) + tandemSuffix);
  }

  cancelStart(): void {
    this.startBooking.set(null);
    this.startAttendeeId.set(null);
    this.startAttendeeServiceId.set(null);
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
      message: 'Do you want to extend this sub-session by 1 hour?',
      confirmText: 'Extend'
    });
    if (!confirmed) return;
    this.http.post(`${environment.apiBaseUrl}/api/sessions/${attendee.sessionId}/extend?minutes=60`, {}).subscribe({
      next: () => this.reload(),
      error: (err) => this.extendError.set(err?.error?.message || 'Could not extend the session.')
    });
  }

  async extendSession(b: BookingResponse): Promise<void> {
    const confirmed = await this.confirmService.confirm({
      title: 'Extend Session',
      message: 'Do you want to extend this session by 1 hour?',
      confirmText: 'Extend'
    });
    if (!confirmed) return;

    this.lookupSession(b.id).subscribe(session => {
      if (!session) return;
      this.http.post(`${environment.apiBaseUrl}/api/sessions/${session.id}/extend?minutes=60`, {}).subscribe({
        next: () => this.reload(),
        error: (err) => this.extendError.set(err?.error?.message || 'Could not extend the session.')
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
