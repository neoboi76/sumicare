import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { ConfirmService } from '../../../shared/components/confirm-dialog/confirm.service';

interface ClientLite {
  id: string;
  nickname: string;
  email?: string | null;
  gender?: string | null;
  nationality?: string | null;
}

interface PackageTier {
  id: number;
  serviceId: number | null;
  serviceCode: string | null;
  serviceName: string | null;
  weekdayPrice: number;
  weekendPrice: number;
}

interface PackageDef {
  id: number;
  code: string;
  name: string;
  description: string | null;
  benefits: string | null;
  maxStayHours: number | null;
  defaultPax: number;
  couple: boolean;
  includesMassage: boolean;
  bundlesPrivateRoom: boolean;
  requiresVipRoom: boolean;
  active: boolean;
  tiers: PackageTier[];
}

interface CartAttendee {
  serviceId: number | null;
  packageTierId: number | null;
  serviceName: string;
  lockerNumber: string;
  clientGender: 'M' | 'F';
}

interface CartItem {
  packageId: number;
  packageCode: string;
  packageName: string;
  includesMassage: boolean;
  requiresVipRoom: boolean;
  tiers: PackageTier[];
  unitPrice: number;
  lineTotal: number;
  attendee: CartAttendee;
}

interface AddedPayment {
  paymentMethod: string;
  amount: number;
  referenceNumber?: string;
}

interface DiscountConfig {
  name: string;
  type: 'DISCOUNT' | 'PRICE_INCREASE';
  amountType: 'PERCENT' | 'FIXED';
  percent: number;
  fixedAmount: number;
  appliedItemIndices: number[];
}

interface OrderCreated {
  id: string;
  bookingId: string;
  treatmentSlipId?: string | null;
  status: string;
  total: number;
  amountPaid: number;
  balance: number;
}

type RoomType = 'COMMON' | 'PRIVATE' | 'VIP';

@Component({
  selector: 'sumi-cashier',
  standalone: true,
  imports: [FormsModule, DecimalPipe, RouterLink],
  templateUrl: './cashier.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CashierComponent implements OnInit {
  private http = inject(HttpClient);
  private router = inject(Router);
  private confirmService = inject(ConfirmService);

  searchTerm = '';
  searchResults = signal<ClientLite[]>([]);
  selectedClient = signal<ClientLite | null>(null);

  transactorName = '';
  packages = signal<PackageDef[]>([]);
  selectedPackageId: number | null = null;
  cart = signal<CartItem[]>([]);

  weekend = signal(false);
  groupBooking = signal(false);
  roomType = signal<RoomType>('COMMON');
  roomAvailability = signal<{ COMMON: number; PRIVATE: number; VIP: number }>({ COMMON: 0, PRIVATE: 0, VIP: 0 });

  referenceNumber = '';
  notes = '';

  paymentMethod = signal<'CASH' | 'GCASH' | 'CREDIT' | 'DEBIT'>('CASH');
  paymentAmount = 0;
  paymentRef = '';
  payments = signal<AddedPayment[]>([]);

  showRegister = signal(false);
  newClient = { nickname: '', email: '', gender: 'F', nationality: '' };
  registerError = signal<string | null>(null);

  showDiscountModal = signal(false);
  discountConfig = signal<DiscountConfig>({
    name: '', type: 'DISCOUNT', amountType: 'PERCENT', percent: 0, fixedAmount: 0, appliedItemIndices: []
  });
  discountTemplates = [
    { label: 'Custom', percent: 0, name: '' },
    { label: 'SENIOR/PWD', percent: 20, name: 'Senior/PWD Discount' }
  ];
  activeTemplate = signal<string>('Custom');
  discountSummary = signal<{ name: string; amount: number }[]>([]);

  submitting = signal(false);
  error = signal<string | null>(null);

  anyVip = computed(() => this.cart().some(c => c.requiresVipRoom));
  effectiveRoomType = computed<RoomType>(() => this.anyVip() ? 'VIP' : this.roomType());
  roomSurcharge = computed(() => this.effectiveRoomType() === 'PRIVATE' ? 500 : 0);

  itemsSubtotal = computed(() => this.cart().reduce((sum, c) => sum + Number(c.lineTotal || 0), 0));
  subtotal = computed(() => this.itemsSubtotal() + this.roomSurcharge());
  totalDiscount = computed(() => this.discountSummary().reduce((sum, d) => sum + d.amount, 0));
  total = computed(() => Math.max(0, this.subtotal() - this.totalDiscount()));
  paid = computed(() => this.payments().reduce((sum, p) => sum + Number(p.amount || 0), 0));
  due = computed(() => Math.max(0, this.total() - this.paid()));

  ngOnInit(): void {
    this.loadPackages();
    this.loadRoomAvailability();
    this.searchClients();
  }

  loadPackages(): void {
    this.http.get<PackageDef[]>(`${environment.apiBaseUrl}/api/cashier/packages`).subscribe({
      next: (p) => this.packages.set(p),
      error: () => this.packages.set([])
    });
  }

  loadRoomAvailability(): void {
    this.http.get<{ COMMON: number; PRIVATE: number; VIP: number }>(
      `${environment.apiBaseUrl}/api/cashier/room-availability`
    ).subscribe({
      next: (a) => this.roomAvailability.set({
        COMMON: a.COMMON ?? 0, PRIVATE: a.PRIVATE ?? 0, VIP: a.VIP ?? 0
      }),
      error: () => this.roomAvailability.set({ COMMON: 0, PRIVATE: 0, VIP: 0 })
    });
  }

  searchClients(): void {
    const q = this.searchTerm ? `?q=${encodeURIComponent(this.searchTerm)}` : '';
    this.http.get<ClientLite[]>(`${environment.apiBaseUrl}/api/clients${q}`).subscribe({
      next: (c) => this.searchResults.set(c),
      error: () => this.searchResults.set([])
    });
  }

  selectClient(c: ClientLite): void {
    this.selectedClient.set(c);
    this.searchResults.set([]);
    this.searchTerm = '';
    if (!this.transactorName.trim()) this.transactorName = c.nickname;
  }

  clearClient(): void {
    this.selectedClient.set(null);
  }

  openRegister(): void {
    this.newClient = { nickname: '', email: '', gender: 'F', nationality: '' };
    this.registerError.set(null);
    this.showRegister.set(true);
  }

  closeRegister(): void {
    this.showRegister.set(false);
  }

  submitRegister(): void {
    if (!this.newClient.nickname.trim()) {
      this.registerError.set('Nickname is required.');
      return;
    }
    this.http.post<ClientLite>(`${environment.apiBaseUrl}/api/clients`, this.newClient).subscribe({
      next: (c) => {
        this.selectClient(c);
        this.showRegister.set(false);
      },
      error: (err) => {
        this.registerError.set(err?.error?.message || 'Could not register client. Try again.');
      }
    });
  }

  addPackage(): void {
    if (this.selectedPackageId == null) return;
    const pkg = this.packages().find(p => p.id === Number(this.selectedPackageId));
    if (!pkg) return;
    const item: CartItem = {
      packageId: pkg.id,
      packageCode: pkg.code,
      packageName: pkg.name,
      includesMassage: pkg.includesMassage,
      requiresVipRoom: pkg.requiresVipRoom,
      tiers: pkg.tiers,
      unitPrice: 0,
      lineTotal: 0,
      attendee: { serviceId: null, packageTierId: null, serviceName: '', lockerNumber: '', clientGender: 'F' }
    };
    if (!pkg.includesMassage && pkg.tiers.length > 0) {
      const tier = pkg.tiers[0];
      item.attendee.packageTierId = tier.id;
      item.unitPrice = this.weekend() ? tier.weekendPrice : tier.weekdayPrice;
      item.lineTotal = item.unitPrice;
    }
    this.cart.update(items => [...items, item]);
    this.selectedPackageId = null;
    if (this.cart().length > 1) this.groupBooking.set(true);
  }

  removeItem(idx: number): void {
    this.cart.update(items => items.filter((_, i) => i !== idx));
    if (this.cart().length <= 1) this.groupBooking.set(false);
  }

  onAttendeeMassageChange(idx: number, tierId: number | null): void {
    this.cart.update(items => items.map((it, i) => {
      if (i !== idx) return it;
      const tier = it.tiers.find(t => t.id === Number(tierId));
      const unit = tier ? (this.weekend() ? tier.weekendPrice : tier.weekdayPrice) : 0;
      return {
        ...it,
        attendee: {
          ...it.attendee,
          packageTierId: tier ? tier.id : null,
          serviceId: tier ? tier.serviceId : null,
          serviceName: tier && tier.serviceName ? tier.serviceName : ''
        },
        unitPrice: unit,
        lineTotal: unit
      };
    }));
  }

  toggleWeekend(): void {
    this.weekend.update(v => !v);
    this.recomputePrices();
  }

  private recomputePrices(): void {
    this.cart.update(items => items.map(it => {
      const tier = it.tiers.find(t => t.id === it.attendee.packageTierId);
      const unit = tier ? (this.weekend() ? tier.weekendPrice : tier.weekdayPrice) : it.unitPrice;
      return { ...it, unitPrice: unit, lineTotal: unit };
    }));
  }

  setRoomType(rt: RoomType): void {
    if (this.anyVip()) return;
    this.roomType.set(rt);
  }

  openDiscountModal(): void {
    const cfg = this.discountConfig();
    cfg.appliedItemIndices = this.cart().map((_, i) => i);
    this.discountConfig.set({ ...cfg });
    this.showDiscountModal.set(true);
  }

  closeDiscountModal(): void {
    this.showDiscountModal.set(false);
  }

  applyTemplate(tmpl: { label: string; percent: number; name: string }): void {
    this.activeTemplate.set(tmpl.label);
    this.discountConfig.update(cfg => ({
      ...cfg,
      name: tmpl.name || tmpl.label,
      amountType: 'PERCENT' as const,
      percent: tmpl.percent,
      fixedAmount: 0,
      type: 'DISCOUNT' as const
    }));
  }

  toggleItemInDiscount(idx: number): void {
    this.discountConfig.update(cfg => {
      const indices = cfg.appliedItemIndices.includes(idx)
        ? cfg.appliedItemIndices.filter(i => i !== idx)
        : [...cfg.appliedItemIndices, idx];
      return { ...cfg, appliedItemIndices: indices };
    });
  }

  isItemInDiscount(idx: number): boolean {
    return this.discountConfig().appliedItemIndices.includes(idx);
  }

  applyDiscount(): void {
    const cfg = this.discountConfig();
    const items = this.cart();
    let totalDiscountAmt = 0;
    if (cfg.type === 'DISCOUNT') {
      for (const idx of cfg.appliedItemIndices) {
        if (idx >= items.length) continue;
        const item = items[idx];
        if (cfg.amountType === 'PERCENT') {
          totalDiscountAmt += item.unitPrice * (cfg.percent / 100);
        } else {
          totalDiscountAmt += cfg.fixedAmount;
        }
      }
    }
    const name = cfg.name || (cfg.amountType === 'PERCENT' ? `${cfg.percent}% discount` : `P${cfg.fixedAmount} discount`);
    if (totalDiscountAmt > 0) {
      this.discountSummary.update(existing => [...existing, { name, amount: totalDiscountAmt }]);
    }
    this.showDiscountModal.set(false);
    this.discountConfig.set({
      name: '', type: 'DISCOUNT', amountType: 'PERCENT', percent: 0, fixedAmount: 0, appliedItemIndices: []
    });
    this.activeTemplate.set('Custom');
  }

  removeDiscountAt(idx: number): void {
    this.discountSummary.update(list => list.filter((_, i) => i !== idx));
  }

  removeAllDiscounts(): void {
    this.discountSummary.set([]);
    this.activeTemplate.set('Custom');
  }

  setPaymentMethod(method: 'CASH' | 'GCASH' | 'CREDIT' | 'DEBIT'): void {
    this.paymentMethod.set(method);
  }

  addPayment(): void {
    const amt = Number(this.paymentAmount || 0);
    if (amt <= 0) return;
    if (this.paid() + amt > this.total()) {
      this.error.set('Payment would exceed the total amount due. Maximum: ' + (this.total() - this.paid()).toFixed(2));
      return;
    }
    this.error.set(null);
    this.payments.update(p => [...p, {
      paymentMethod: this.paymentMethod(),
      amount: amt,
      referenceNumber: this.paymentRef || undefined
    }]);
    this.paymentAmount = 0;
    this.paymentRef = '';
  }

  async removePayment(idx: number): Promise<void> {
    const confirmed = await this.confirmService.confirm({
      title: 'Remove Payment',
      message: 'Are you sure you want to remove this payment entry?',
      confirmText: 'Remove',
      danger: true
    });
    if (!confirmed) return;
    this.payments.update(p => p.filter((_, i) => i !== idx));
  }

  private validate(): string | null {
    if (!this.transactorName.trim()) return 'Enter a transactor name.';
    if (this.cart().length === 0) return 'Add at least one package.';
    for (let i = 0; i < this.cart().length; i++) {
      const it = this.cart()[i];
      const a = it.attendee;
      if (it.includesMassage && (a.serviceId == null || a.packageTierId == null)) {
        return `Item ${i + 1} (${it.packageName}): choose a massage.`;
      }
      if (!a.lockerNumber.trim()) return `Item ${i + 1} (${it.packageName}): enter a locker number.`;
      if (!a.clientGender) return `Item ${i + 1} (${it.packageName}): choose a sex.`;
    }
    if (this.effectiveRoomType() === 'VIP' && !this.anyVip()) {
      return 'VIP room requires a VIP package.';
    }
    return null;
  }

  async checkout(): Promise<void> {
    if (this.submitting()) return;
    const err = this.validate();
    if (err) {
      this.error.set(err);
      return;
    }
    const confirmed = await this.confirmService.confirm({
      title: 'Confirm Checkout',
      message: `Process an order for ${this.transactorName} totaling P${this.total().toFixed(2)} (${this.cart().length} item(s))?`,
      confirmText: 'Checkout'
    });
    if (!confirmed) return;

    this.submitting.set(true);
    this.error.set(null);

    const first = this.cart()[0];
    const firstPayment = this.payments().length > 0 ? this.payments()[0] : null;
    const payload = {
      clientId: this.selectedClient()?.id || null,
      clientNickname: this.transactorName,
      clientGender: first.attendee.clientGender,
      transactorName: this.transactorName,
      groupBooking: this.cart().length > 1 || this.groupBooking(),
      weekend: this.weekend(),
      roomType: this.effectiveRoomType(),
      roomTypeCharge: this.roomSurcharge(),
      pax: this.cart().length,
      lockerNumber: first.attendee.lockerNumber || null,
      referenceNumber: this.referenceNumber || null,
      notes: this.notes || null,
      subtotal: this.subtotal(),
      discount: this.totalDiscount(),
      total: this.total(),
      items: this.cart().map((c, i) => ({
        packageId: c.packageId,
        quantity: 1,
        unitPrice: c.unitPrice,
        lineTotal: c.lineTotal,
        position: i,
        attendees: [{
          serviceId: c.attendee.serviceId,
          packageTierId: c.attendee.packageTierId,
          lockerNumber: c.attendee.lockerNumber,
          clientGender: c.attendee.clientGender,
          position: 0
        }]
      })),
      initialPayment: firstPayment ? {
        paymentMethod: firstPayment.paymentMethod,
        amount: firstPayment.amount,
        referenceNumber: firstPayment.referenceNumber || null
      } : null
    };

    this.http
      .post<OrderCreated>(`${environment.apiBaseUrl}/api/cashier/orders`, payload)
      .subscribe({
        next: (order) => {
          const extra = this.payments().slice(1);
          if (extra.length === 0) {
            this.submitting.set(false);
            this.router.navigate(['/app/orders', order.id]);
            return;
          }
          this.recordExtraPayments(order.id, extra, 0);
        },
        error: (err) => {
          this.submitting.set(false);
          this.error.set(err?.error?.message || 'Could not create order.');
        }
      });
  }

  private recordExtraPayments(orderId: string, list: AddedPayment[], idx: number): void {
    if (idx >= list.length) {
      this.submitting.set(false);
      this.router.navigate(['/app/orders', orderId]);
      return;
    }
    const p = list[idx];
    this.http.post(`${environment.apiBaseUrl}/api/cashier/orders/${orderId}/payments`, {
      paymentMethod: p.paymentMethod,
      amount: p.amount,
      referenceNumber: p.referenceNumber || null
    }).subscribe({
      next: () => this.recordExtraPayments(orderId, list, idx + 1),
      error: () => {
        this.submitting.set(false);
        this.router.navigate(['/app/orders', orderId]);
      }
    });
  }
}
