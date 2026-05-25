import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { QRCodeComponent } from 'angularx-qrcode';
import { ActivatedRoute, Router } from '@angular/router';
import { environment } from '../../../environments/environment';
import { PaymentDetailsModalComponent } from '../../shared/components/payment-details/payment-details-modal.component';
import { PaymentDetails, PaymentDetailsService } from '../../shared/components/payment-details/payment-details.service';

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
  orderId: string | null;
}

type PayMethod = 'GCASH' | 'CREDIT' | 'DEBIT';

interface PublicPaymentResult {
  status: string;
  intentId: string | null;
  redirectUrl: string | null;
  orNumber: string | null;
  clientNickname: string | null;
  packageName: string | null;
  serviceName: string | null;
  scheduledAt: string | null;
  reservationType: string | null;
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
  imports: [FormsModule, DecimalPipe, QRCodeComponent, PaymentDetailsModalComponent],
  templateUrl: './book.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class BookComponent implements OnInit {
  private http = inject(HttpClient);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private paymentDetailsService = inject(PaymentDetailsService);

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
  orNumber = signal<string | null>(null);
  confirmNickname = signal<string | null>(null);
  confirmReservationType = signal<string | null>(null);
  confirmScheduled = signal<string | null>(null);
  confirmPackageName = signal<string | null>(null);
  confirmServiceName = signal<string | null>(null);

  clientNickname = '';
  clientEmail = '';
  nationality = '';
  packageId = signal<number | null>(null);
  reservationType = 'SOFT';
  paymentMethod = signal<PayMethod>('GCASH');
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

  estimatedTotal = computed(() => {
    const tiers = this.packageTiers();
    const atts = this.attendees();
    let sum = 0;
    if (this.forcedDoubleGuests()) {
      const t = tiers.find(x => x.id === Number(atts[0]?.packageTierId));
      sum = t ? t.weekdayPrice : 0;
    } else {
      for (const a of atts) {
        const t = tiers.find(x => x.id === Number(a.packageTierId));
        sum += t ? t.weekdayPrice : 0;
      }
    }
    const room = !this.forcedVipRoom() && this.roomType() === 'PRIVATE' ? 500 : 0;
    return sum + room;
  });

  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    if (params.get('paymongoReturn')) {
      this.handlePaymentReturn(params.get('orderId'), params.get('intent'), params.get('paymentMethod'), params.get('status'));
    }
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
      paymentMethod: this.reservationType === 'HARD' ? this.paymentMethod() : null,
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
          if (this.reservationType === 'HARD' && booking.orderId) {
            this.startPublicPayment(booking);
            return;
          }
          this.submitting.set(false);
          const ref = booking.id.slice(0, 8).toUpperCase();
          this.bookingRef.set(ref);
          this.setConfirmationFromForm(booking);
          this.confirmation.set(booking);
        },
        error: (err) => {
          this.submitting.set(false);
          this.error.set(this.extractErrorMessage(err));
        }
      });
  }

  private setConfirmationFromForm(booking: BookingCreated): void {
    this.confirmNickname.set(booking.clientNickname || this.clientNickname.trim() || null);
    this.confirmReservationType.set(booking.reservationType);
    this.confirmScheduled.set(booking.scheduledAt);
    this.confirmPackageName.set(this.selectedPackage()?.name ?? null);
    this.confirmServiceName.set(this.services().find(s => s.id === booking.serviceId)?.name ?? null);
  }

  private setConfirmationFromResponse(res: PublicPaymentResult): void {
    this.confirmNickname.set(res.clientNickname);
    this.confirmReservationType.set(res.reservationType ?? 'HARD');
    this.confirmScheduled.set(res.scheduledAt);
    this.confirmPackageName.set(res.packageName);
    this.confirmServiceName.set(res.serviceName);
  }

  private async startPublicPayment(booking: BookingCreated): Promise<void> {
    const method = this.paymentMethod();
    let details: PaymentDetails | null = null;
    if (method === 'GCASH') {
      details = await this.paymentDetailsService.open(method, this.estimatedTotal());
      if (!details) {
        this.submitting.set(false);
        return;
      }
    }
    this.http
      .post<PublicPaymentResult>(
        `${environment.apiBaseUrl}/api/public/bookings/${environment.defaultOrganizationSlug}/payment/initiate`,
        { orderId: booking.orderId, paymentMethod: method, paymentDetails: details }
      )
      .subscribe({
        next: (res) => this.onPaymentInitiated(booking, res, method),
        error: (err) => {
          this.submitting.set(false);
          this.error.set(this.extractErrorMessage(err));
        }
      });
  }

  private onPaymentInitiated(booking: BookingCreated, res: PublicPaymentResult, method: PayMethod): void {
    if (res.status === 'succeeded') {
      this.submitting.set(false);
      this.bookingRef.set(booking.id.slice(0, 8).toUpperCase());
      this.orNumber.set(res.orNumber);
      this.setConfirmationFromResponse(res);
      this.confirmation.set(booking);
      return;
    }
    const origin = window.location.origin;
    const returnUrl = `${origin}/book?paymongoReturn=1&orderId=${booking.orderId}`
      + `&intent=${encodeURIComponent(res.intentId ?? '')}`
      + `&paymentMethod=${encodeURIComponent(method)}`;
    if (booking.orderId && res.intentId) {
      sessionStorage.setItem('paymongoIntent:' + booking.orderId, res.intentId);
    }
    if (res.intentId && res.intentId.startsWith('mock_')) {
      window.location.href = `${origin}/pay/authorize?intent=${encodeURIComponent(res.intentId)}`
        + `&amount=${this.estimatedTotal()}`
        + `&method=${encodeURIComponent(method)}`
        + `&return=${encodeURIComponent(returnUrl)}`;
    } else if (res.redirectUrl) {
      window.location.href = res.redirectUrl;
    } else {
      this.submitting.set(false);
      this.error.set('Could not start the payment. Please try again.');
    }
  }

  private handlePaymentReturn(orderId: string | null, intentId: string | null, method: string | null, status: string | null): void {
    if (!orderId) return;
    const resolvedIntent = intentId ?? sessionStorage.getItem('paymongoIntent:' + orderId);
    sessionStorage.removeItem('paymongoIntent:' + orderId);
    if (status === 'cancelled' || status === 'failed') {
      this.error.set('The payment was cancelled. Your slot is not yet reserved. Please try again.');
      this.router.navigate(['/book']);
      return;
    }
    if (!resolvedIntent) {
      this.error.set('We could not confirm your payment. If you were charged, please contact the front desk with your reference.');
      this.router.navigate(['/book']);
      return;
    }
    this.submitting.set(true);
    this.http
      .post<PublicPaymentResult>(
        `${environment.apiBaseUrl}/api/public/bookings/${environment.defaultOrganizationSlug}/payment/confirm`,
        { orderId, intentId: resolvedIntent, paymentMethod: method }
      )
      .subscribe({
        next: (res) => {
          this.submitting.set(false);
          this.bookingRef.set(orderId.slice(0, 8).toUpperCase());
          this.orNumber.set(res.orNumber);
          this.setConfirmationFromResponse(res);
          this.confirmation.set({
            id: orderId,
            clientNickname: res.clientNickname ?? '',
            reservationType: res.reservationType ?? 'HARD',
            scheduledAt: res.scheduledAt ?? '',
            serviceId: 0,
            orderId
          });
        },
        error: () => {
          this.submitting.set(false);
          this.error.set('We could not confirm your payment. If you were charged, please contact the front desk with your reference.');
        }
      });
  }

  bookAnother(): void {
    this.confirmation.set(null);
    this.bookingRef.set(null);
    this.orNumber.set(null);
    this.confirmNickname.set(null);
    this.confirmReservationType.set(null);
    this.confirmScheduled.set(null);
    this.confirmPackageName.set(null);
    this.confirmServiceName.set(null);
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
