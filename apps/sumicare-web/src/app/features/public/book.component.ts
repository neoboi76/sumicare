import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { QRCodeComponent } from 'angularx-qrcode';
import { ActivatedRoute } from '@angular/router';
import { environment } from '../../../environments/environment';

interface ServiceItem {
  id: number;
  name: string;
  durationMinutes: number;
  price: number;
  fixedRate: boolean;
}

interface PublicPackageTier {
  id: number;
  serviceId: number | null;
  serviceName: string | null;
  weekdayPrice: number;
  weekendPrice: number;
}

interface PublicPackage {
  id: number;
  code: string;
  name: string;
  description: string | null;
  defaultPax: number;
  couple: boolean;
  includesMassage: boolean;
  requiresVipRoom: boolean;
  active: boolean;
  tiers: PublicPackageTier[];
}

interface BookingCreated {
  id: string;
  clientNickname: string;
  reservationType: string;
  scheduledAt: string;
  serviceId: number;
}

interface AttendeeForm {
  packageTierId: number | null;
  lockerNumber: string;
  clientGender: 'M' | 'F';
}

type RoomType = 'COMMON' | 'PRIVATE' | 'VIP';

@Component({
  selector: 'sumi-book',
  standalone: true,
  imports: [FormsModule, QRCodeComponent],
  templateUrl: './book.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class BookComponent implements OnInit {
  private http = inject(HttpClient);
  private route = inject(ActivatedRoute);

  services = signal<ServiceItem[]>([]);
  packages = signal<PublicPackage[]>([]);
  error = signal<string | null>(null);
  submitting = signal(false);
  loadingInitial = signal(true);
  private servicesReady = false;
  private packagesReady = false;
  private markReady(which: 'services' | 'packages'): void {
    if (which === 'services') this.servicesReady = true;
    else this.packagesReady = true;
    if (this.servicesReady && this.packagesReady) this.loadingInitial.set(false);
  }
  confirmation = signal<BookingCreated | null>(null);
  bookingRef = signal<string | null>(null);

  clientNickname = '';
  clientEmail = '';
  nationality = '';
  packageId = signal<number | null>(null);
  reservationType = 'SOFT';
  scheduledDate = '';
  scheduledTime = '';
  consent = false;
  roomType = signal<RoomType>('COMMON');
  pax = signal(1);
  attendees = signal<AttendeeForm[]>([this.blankAttendee()]);

  selectedPackage = computed(() => {
    const id = this.packageId();
    if (id == null) return null;
    return this.packages().find(p => p.id === Number(id)) ?? null;
  });
  packageTiers = computed(() => this.selectedPackage()?.tiers ?? []);

  forcedDoubleGuests = computed(() => {
    const p = this.selectedPackage();
    return !!p && (p.couple || p.requiresVipRoom);
  });

  forcedVipRoom = computed(() => this.selectedPackage()?.requiresVipRoom ?? false);

  serviceLabel = computed(() => {
    const id = this.confirmation()?.serviceId;
    if (id == null) return '';
    return this.services().find(s => s.id === id)?.name ?? '';
  });

  ngOnInit(): void {
    this.http
      .get<ServiceItem[]>(`${environment.apiBaseUrl}/api/public/services/${environment.defaultOrganizationSlug}`)
      .subscribe({
        next: (s) => { this.services.set(s); this.markReady('services'); },
        error: () => { this.services.set([]); this.markReady('services'); }
      });
    this.http
      .get<PublicPackage[]>(`${environment.apiBaseUrl}/api/public/packages/${environment.defaultOrganizationSlug}`)
      .subscribe({
        next: (p) => { this.packages.set(p.filter(pkg => pkg.active)); this.markReady('packages'); },
        error: () => { this.packages.set([]); this.markReady('packages'); }
      });
  }

  onPackageChange(): void {
    if (this.forcedVipRoom()) {
      this.roomType.set('VIP');
    } else if (this.roomType() === 'VIP') {
      this.roomType.set('COMMON');
    }
    const pkg = this.selectedPackage();
    if (pkg && (pkg.couple || pkg.requiresVipRoom)) {
      const forced = Math.max(2, pkg.defaultPax);
      this.pax.set(forced);
      this.syncAttendees(forced, true);
    } else {
      this.syncAttendees(this.pax(), false);
    }
  }

  setRoomType(rt: RoomType): void {
    if (this.forcedVipRoom()) return;
    this.roomType.set(rt);
  }

  setPax(n: number): void {
    if (this.forcedDoubleGuests()) return;
    const next = Math.max(1, Math.min(12, n || 1));
    this.pax.set(next);
    this.syncAttendees(next, false);
  }

  setAttendeeTier(idx: number, tierId: number | null): void {
    const list = [...this.attendees()];
    if (!list[idx]) return;
    list[idx] = { ...list[idx], packageTierId: tierId };
    if (this.forcedDoubleGuests()) {
      for (let i = 0; i < list.length; i++) {
        list[i] = { ...list[i], packageTierId: tierId };
      }
    }
    this.attendees.set(list);
  }

  setAttendeeLocker(idx: number, locker: string): void {
    const list = [...this.attendees()];
    if (!list[idx]) return;
    list[idx] = { ...list[idx], lockerNumber: locker };
    this.attendees.set(list);
  }

  setAttendeeGender(idx: number, gender: 'M' | 'F'): void {
    const list = [...this.attendees()];
    if (!list[idx]) return;
    list[idx] = { ...list[idx], clientGender: gender };
    this.attendees.set(list);
  }

  private blankAttendee(): AttendeeForm {
    return { packageTierId: null, lockerNumber: '', clientGender: 'F' };
  }

  private syncAttendees(count: number, sharedTier: boolean): void {
    const current = this.attendees();
    const next: AttendeeForm[] = [];
    for (let i = 0; i < count; i++) {
      const existing = current[i];
      next.push(existing ? { ...existing } : this.blankAttendee());
    }
    if (sharedTier && next.length > 0) {
      const tier = next[0].packageTierId;
      for (let i = 1; i < next.length; i++) {
        next[i] = { ...next[i], packageTierId: tier };
      }
    }
    this.attendees.set(next);
  }

  submit(event: Event): void {
    event.preventDefault();
    if (this.submitting()) return;
    const missing: string[] = [];
    if (!this.clientNickname.trim()) missing.push('nickname');
    if (!this.clientEmail.trim()) missing.push('email');
    if (this.packageId() == null) missing.push('package');
    const atts = this.attendees();
    for (let i = 0; i < atts.length; i++) {
      if (atts[i].packageTierId == null) {
        missing.push(`guest ${i + 1} massage`);
      }
    }
    if (!this.scheduledDate) missing.push('date');
    if (!this.scheduledTime) missing.push('time');
    if (!this.consent) missing.push('consent');
    if (missing.length > 0) {
      this.error.set('Please complete: ' + missing.join(', ') + '.');
      return;
    }

    const firstTier = this.packageTiers().find(t => t.id === Number(atts[0].packageTierId));
    const firstServiceId = firstTier?.serviceId ?? null;
    if (firstServiceId == null) {
      this.error.set('The selected massage is not bookable. Please pick another.');
      return;
    }

    const combined = new Date(`${this.scheduledDate}T${this.scheduledTime}`);
    if (isNaN(combined.getTime())) {
      this.error.set('Please enter a valid date and time.');
      return;
    }

    this.submitting.set(true);
    this.error.set(null);

    const payload = {
      clientNickname: this.clientNickname.trim(),
      clientEmail: this.clientEmail.trim(),
      nationality: this.nationality.trim() || null,
      serviceId: Number(firstServiceId),
      reservationType: this.reservationType,
      scheduledAt: combined.toISOString(),
      clientGender: atts[0].clientGender,
      packageId: Number(this.packageId()),
      packageTierId: Number(atts[0].packageTierId),
      lockerNumber: null,
      pax: atts.length,
      roomType: this.forcedVipRoom() ? 'VIP' : this.roomType(),
      attendees: atts.map(a => ({
        packageTierId: a.packageTierId,
        lockerNumber: null,
        clientGender: a.clientGender
      }))
    };

    this.http
      .post<BookingCreated>(
        `${environment.apiBaseUrl}/api/public/bookings/${environment.defaultOrganizationSlug}`,
        payload
      )
      .subscribe({
        next: (booking) => {
          this.submitting.set(false);
          const ref = booking.id.slice(0, 8).toUpperCase();
          this.bookingRef.set(ref);
          this.confirmation.set(booking);
        },
        error: (err) => {
          this.submitting.set(false);
          this.error.set(this.extractErrorMessage(err));
        }
      });
  }

  bookAnother(): void {
    this.confirmation.set(null);
    this.bookingRef.set(null);
    this.error.set(null);
    this.clientNickname = '';
    this.clientEmail = '';
    this.nationality = '';
    this.scheduledDate = '';
    this.scheduledTime = '';
    this.consent = false;
    this.packageId.set(null);
    this.pax.set(1);
    this.attendees.set([this.blankAttendee()]);
    this.roomType.set('COMMON');
  }

  formatDateTime(iso: string | null): string {
    if (!iso) return '';
    return new Date(iso).toLocaleString('en-PH', {
      dateStyle: 'long',
      timeStyle: 'short'
    });
  }

  private extractErrorMessage(err: unknown): string {
    if (typeof err === 'object' && err !== null) {
      const anyErr = err as { error?: { message?: string }, message?: string, status?: number };
      if (anyErr.error?.message) return anyErr.error.message;
      if (anyErr.status === 0) return 'Could not reach the booking server. Please check your connection and try again.';
      if (anyErr.status === 400) return 'One or more fields are invalid. Please review the form.';
      if (anyErr.message) return anyErr.message;
    }
    return 'Booking failed. Please try again or visit us in person.';
  }
}
