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
  serviceDurationMinutes: number | null;
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

interface BookingItemForm {
  packageId: number | null;
  roomType: RoomType;
  attendees: AttendeeForm[];
}

interface AppliedVoucher {
  code: string;
  name: string | null;
  discountAmount: number | null;
  discountPercent: number | null;
  targetPackageId: number | null;
}

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
  reservationType = 'SOFT';
  paymentMethod = signal<PayMethod>('GCASH');
  scheduledDate = '';
  scheduledTime = '';
  consent = false;
  bookingItems = signal<BookingItemForm[]>([this.blankItem()]);

  voucherCode = '';
  voucherError = signal<string | null>(null);
  appliedVoucher = signal<AppliedVoucher | null>(null);

  private itemAmount(item: BookingItemForm): number {
    const pkg = this.packageById(item.packageId);
    if (!pkg) return 0;
    let sum = 0;
    if (this.isDoubleItem(pkg)) {
      const t = pkg.tiers.find(x => x.id === Number(item.attendees[0]?.packageTierId));
      sum += t ? t.weekdayPrice : 0;
    } else {
      for (const a of item.attendees) {
        const t = pkg.tiers.find(x => x.id === Number(a.packageTierId));
        sum += t ? t.weekdayPrice : 0;
      }
    }
    if (item.roomType === 'PRIVATE' && !pkg.requiresVipRoom) sum += 500;
    return sum;
  }

  grossTotal = computed(() => this.bookingItems().reduce((sum, item) => sum + this.itemAmount(item), 0));

  voucherDiscount = computed(() => {
    const v = this.appliedVoucher();
    if (!v) return 0;
    let base: number;
    if (v.targetPackageId != null) {
      base = this.bookingItems()
        .filter(item => Number(item.packageId) === v.targetPackageId)
        .reduce((sum, item) => sum + this.itemAmount(item), 0);
      if (base <= 0) return 0;
    } else {
      base = this.grossTotal();
    }
    if (v.discountAmount != null) return Math.min(v.discountAmount, base);
    if (v.discountPercent != null) return Math.round(base * v.discountPercent) / 100;
    return 0;
  });

  estimatedTotal = computed(() => Math.max(0, this.grossTotal() - this.voucherDiscount()));

  applyVoucher(): void {
    const code = this.voucherCode.trim();
    this.voucherError.set(null);
    if (!code) {
      this.appliedVoucher.set(null);
      return;
    }
    this.http
      .get<AppliedVoucher>(`${environment.apiBaseUrl}/api/public/vouchers/${environment.defaultOrganizationSlug}/check?code=${encodeURIComponent(code)}`)
      .subscribe({
        next: (v) => {
          this.appliedVoucher.set({ ...v, code });
          if (this.voucherDiscount() <= 0) {
            this.voucherError.set('This voucher does not apply to the selected packages.');
          }
        },
        error: () => {
          this.appliedVoucher.set(null);
          this.voucherError.set('Voucher invalid or already redeemed.');
        }
      });
  }

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

  packageById(id: number | null): PublicPackage | null {
    if (id == null) return null;
    return this.packages().find(p => p.id === Number(id)) ?? null;
  }

  tiersFor(item: BookingItemForm): PublicPackageTier[] {
    const pkg = this.packageById(item.packageId);
    const tiers = pkg?.tiers ?? [];
    if (pkg?.requiresVipRoom) {
      return tiers.filter(t => t.serviceDurationMinutes == null || t.serviceDurationMinutes <= 60);
    }
    return tiers;
  }

  isDoubleItem(pkg: PublicPackage | null): boolean {
    return !!pkg && (pkg.couple || pkg.requiresVipRoom);
  }

  isVipItem(pkg: PublicPackage | null): boolean {
    return !!pkg && pkg.requiresVipRoom;
  }

  private blankItem(): BookingItemForm {
    return { packageId: null, roomType: 'COMMON', attendees: [this.blankAttendee()] };
  }

  private blankAttendee(): AttendeeForm {
    return { packageTierId: null, lockerNumber: '', clientGender: 'F' };
  }

  addItem(): void {
    this.bookingItems.update(items => [...items, this.blankItem()]);
  }

  removeItem(idx: number): void {
    this.bookingItems.update(items => items.length <= 1 ? items : items.filter((_, i) => i !== idx));
  }

  private cloneItems(items: BookingItemForm[]): BookingItemForm[] {
    return items.map(it => ({ ...it, attendees: it.attendees.map(a => ({ ...a })) }));
  }

  onItemPackageChange(idx: number, packageId: number | null): void {
    this.bookingItems.update(items => {
      const next = this.cloneItems(items);
      const item = next[idx];
      if (!item) return items;
      item.packageId = packageId;
      const pkg = this.packageById(packageId);
      if (pkg?.requiresVipRoom) item.roomType = 'VIP';
      else if (item.roomType === 'VIP') item.roomType = 'COMMON';
      const count = this.isDoubleItem(pkg) ? Math.max(2, pkg!.defaultPax) : 1;
      const attendees: AttendeeForm[] = [];
      for (let i = 0; i < count; i++) {
        attendees.push(item.attendees[i] ? { ...item.attendees[i] } : this.blankAttendee());
      }
      item.attendees = attendees;
      return next;
    });
  }

  setItemRoom(idx: number, rt: RoomType): void {
    this.bookingItems.update(items => {
      const next = this.cloneItems(items);
      const item = next[idx];
      if (!item) return items;
      if (this.packageById(item.packageId)?.requiresVipRoom) return items;
      item.roomType = rt;
      return next;
    });
  }

  setItemAttendeeTier(idx: number, attIdx: number, tierId: number | null): void {
    this.bookingItems.update(items => {
      const next = this.cloneItems(items);
      const item = next[idx];
      if (!item || !item.attendees[attIdx]) return items;
      item.attendees[attIdx].packageTierId = tierId;
      if (this.isDoubleItem(this.packageById(item.packageId))) {
        for (const a of item.attendees) a.packageTierId = tierId;
      }
      return next;
    });
  }

  setItemAttendeeGender(idx: number, attIdx: number, gender: 'M' | 'F'): void {
    this.bookingItems.update(items => {
      const next = this.cloneItems(items);
      const item = next[idx];
      if (!item || !item.attendees[attIdx]) return items;
      item.attendees[attIdx].clientGender = gender;
      return next;
    });
  }

  submit(event: Event): void {
    event.preventDefault();
    if (this.submitting()) return;
    const missing: string[] = [];
    if (!this.clientNickname.trim()) missing.push('nickname');
    if (!this.clientEmail.trim()) missing.push('email');
    const items = this.bookingItems();
    items.forEach((item, idx) => {
      if (item.packageId == null) missing.push(`package ${idx + 1}`);
      item.attendees.forEach((a, gi) => {
        if (a.packageTierId == null) missing.push(`package ${idx + 1} guest ${gi + 1} massage`);
      });
    });
    if (!this.scheduledDate) missing.push('date');
    if (!this.scheduledTime) missing.push('time');
    if (!this.consent) missing.push('consent');
    if (missing.length > 0) {
      this.error.set('Please complete: ' + missing.join(', ') + '.');
      return;
    }

    const firstItem = items[0];
    const firstPkg = this.packageById(firstItem.packageId);
    const firstTier = (firstPkg?.tiers ?? []).find(t => t.id === Number(firstItem.attendees[0].packageTierId));
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

    const payloadItems = items.map(item => {
      const pkg = this.packageById(item.packageId);
      const isDouble = this.isDoubleItem(pkg);
      return {
        packageId: Number(item.packageId),
        packageTierId: isDouble ? Number(item.attendees[0].packageTierId) : null,
        roomType: pkg?.requiresVipRoom ? 'VIP' : item.roomType,
        attendees: item.attendees.map(a => ({
          packageTierId: a.packageTierId,
          lockerNumber: null,
          clientGender: a.clientGender
        }))
      };
    });

    const payload = {
      clientNickname: this.clientNickname.trim(),
      clientEmail: this.clientEmail.trim(),
      nationality: this.nationality.trim() || null,
      serviceId: Number(firstServiceId),
      reservationType: this.reservationType,
      scheduledAt: combined.toISOString(),
      clientGender: firstItem.attendees[0].clientGender,
      paymentMethod: this.reservationType === 'HARD' ? this.paymentMethod() : null,
      items: payloadItems,
      voucherCode: this.appliedVoucher() ? this.voucherCode.trim() : null
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
    this.confirmPackageName.set(this.packageById(this.bookingItems()[0]?.packageId ?? null)?.name ?? null);
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
    this.bookingItems.set([this.blankItem()]);
    this.voucherCode = '';
    this.appliedVoucher.set(null);
    this.voucherError.set(null);
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
