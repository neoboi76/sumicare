import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, ParamMap, Router, RouterLink } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { ConfirmService } from '../../../shared/components/confirm-dialog/confirm.service';
import { PaymentDetailsModalComponent } from '../../../shared/components/payment-details/payment-details-modal.component';
import { PaymentDetails, PaymentDetailsService } from '../../../shared/components/payment-details/payment-details.service';
import { manilaToday, manilaNowTime, toManilaIso } from '../../../shared/util/manila-time';

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
  serviceDurationMinutes: number | null;
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
  inclusions: string[];
  tiers: PackageTier[];
}

interface CartAttendee {
  serviceId: number | null;
  packageTierId: number | null;
  serviceName: string;
  lockerNumber: string;
  clientGender: 'M' | 'F';
  discount: number;
  providedTsn: string | null;
}

interface CartItem {
  packageId: number;
  packageCode: string;
  packageName: string;
  includesMassage: boolean;
  requiresVipRoom: boolean;
  couple: boolean;
  defaultPax: number;
  tiers: PackageTier[];
  inclusions: string[];
  roomType: RoomType;
  unitPrice: number;
  lineTotal: number;
  attendees: CartAttendee[];
}

interface AddedPayment {
  paymentMethod: string;
  amount: number;
  referenceNumber?: string;
  paymentDetails?: PaymentDetails;
}

interface DiscountConfig {
  name: string;
  type: 'DISCOUNT' | 'PRICE_INCREASE';
  amountType: 'PERCENT' | 'FIXED';
  percent: number;
  fixedAmount: number;
  appliedItemIndices: number[];
}

interface DiscountTemplate {
  label: string;
  percent: number;
  name: string;
  id?: string;
  amountType?: 'PERCENT' | 'FIXED';
  fixedAmount?: number;
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
  imports: [FormsModule, DecimalPipe, RouterLink, PaymentDetailsModalComponent],
  templateUrl: './cashier.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CashierComponent implements OnInit {
  private http = inject(HttpClient);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private confirmService = inject(ConfirmService);
  private paymentDetailsService = inject(PaymentDetailsService);

  searchTerm = '';
  searchResults = signal<ClientLite[]>([]);
  selectedClient = signal<ClientLite | null>(null);
  clientLocked = signal(false);

  transactorName = '';
  scheduleDate = '';
  scheduleTime = '';
  packages = signal<PackageDef[]>([]);
  selectedPackageId: number | null = null;
  cart = signal<CartItem[]>([]);

  weekend = signal(false);
  groupBooking = computed(() => this.cart().length > 1 || this.cart().some(c => c.couple || c.requiresVipRoom));

  tiersForItem(item: CartItem): PackageTier[] {
    return item.tiers;
  }
  roomAvailability = signal<{ COMMON: number; PRIVATE: number; VIP: number }>({ COMMON: 0, PRIVATE: 0, VIP: 0 });

  referenceNumber = '';
  orNumber = '';
  tsNumber = '';
  notes = '';

  editingOrderId = signal<string | null>(null);

  voucherCode = '';
  voucherId = signal<string | null>(null);
  voucherError = signal<string | null>(null);

  paymentMethod = signal<string>('CASH');
  paymentAmount = 0;
  paymentRef = '';
  payments = signal<AddedPayment[]>([]);

  chargeLedgers = signal<{ name: string; shortName: string }[]>([]);
  chargeOptions = computed<{ value: string; label: string }[]>(() => [
    { value: 'CASH', label: 'Cash' },
    { value: 'GCASH', label: 'GCash' },
    { value: 'CREDIT', label: 'Credit card' },
    { value: 'DEBIT', label: 'Debit card' },
    ...this.chargeLedgers().map(l => ({ value: l.shortName, label: l.name }))
  ]);

  showRegister = signal(false);
  newClient = { nickname: '', email: '', gender: 'F', nationality: '' };
  registerError = signal<string | null>(null);

  showDiscountModal = signal(false);
  discountConfig = signal<DiscountConfig>({
    name: '', type: 'DISCOUNT', amountType: 'PERCENT', percent: 0, fixedAmount: 0, appliedItemIndices: []
  });
  builtinTemplates: DiscountTemplate[] = [
    { label: 'Custom', percent: 0, name: '' },
    { label: 'SENIOR/PWD', percent: 28.5714, name: 'Senior/PWD Discount' }
  ];
  savedTemplates = signal<DiscountTemplate[]>([]);
  discountTemplates = computed<DiscountTemplate[]>(() => [...this.builtinTemplates, ...this.savedTemplates()]);
  activeTemplate = signal<string>('Custom');
  discountSummary = signal<{ name: string; amount: number }[]>([]);

  hasVoucherApplied = computed(() => this.voucherId() !== null);
  hasManualDiscount = computed(() => this.discountSummary().some(d => !d.name.startsWith('Voucher ')));

  submitting = signal(false);
  error = signal<string | null>(null);
  tax = signal(0);

  roomSurcharge = computed(() =>
    this.cart().reduce((sum, c) => sum + (!c.requiresVipRoom && c.roomType === 'PRIVATE' ? 500 : 0), 0));

  totalAttendees = computed(() => this.cart().reduce((sum, c) => sum + c.attendees.length, 0));
  itemsSubtotal = computed(() => this.cart().reduce((sum, c) => sum + Number(c.lineTotal || 0), 0));
  subtotal = computed(() => this.itemsSubtotal() + this.roomSurcharge());
  attendeeDiscountTotal = computed(() =>
    this.cart().reduce((sum, c) =>
      sum + c.attendees.reduce((acc, a) => acc + Number(a.discount || 0), 0), 0));
  totalDiscount = computed(() =>
    this.discountSummary().reduce((sum, d) => sum + d.amount, 0) + this.attendeeDiscountTotal());
  total = computed(() => Math.max(0, this.subtotal() - this.totalDiscount() + this.tax()));
  paid = computed(() => this.payments().reduce((sum, p) => sum + Number(p.amount || 0), 0));
  due = computed(() => Math.max(0, this.total() - this.paid()));

  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    if (params.get('paymongoReturn')) {
      this.handlePayMongoReturn(params);
      return;
    }
    this.loadRoomAvailability();
    this.searchClients();
    this.loadDiscountTemplates();
    this.loadChargeLedgers();
    const orderId = params.get('orderId');
    if (!orderId) {
      this.scheduleDate = manilaToday();
      this.scheduleTime = manilaNowTime();
    }
    this.loadPackages(orderId);
  }

  private loadChargeLedgers(): void {
    this.http.get<{ name: string; shortName: string }[]>(`${environment.apiBaseUrl}/api/cashier/ledger/accounts`).subscribe({
      next: (l) => this.chargeLedgers.set(l),
      error: () => this.chargeLedgers.set([])
    });
  }

  private handlePayMongoReturn(params: ParamMap): void {
    const orderId = params.get('orderId');
    const status = params.get('status');
    const paymentMethod = params.get('paymentMethod');
    const amount = params.get('amount');
    if (!orderId) {
      this.router.navigate(['/app/cashier']);
      return;
    }
    const intent = params.get('intent') ?? sessionStorage.getItem('paymongoIntent:' + orderId);
    sessionStorage.removeItem('paymongoIntent:' + orderId);
    if (status === 'cancelled' || status === 'failed') {
      this.router.navigate(['/app/orders', orderId], { queryParams: { paymentError: 'cancelled' } });
      return;
    }
    if (!intent) {
      this.router.navigate(['/app/orders', orderId], { queryParams: { paymentError: 'confirm_failed' } });
      return;
    }
    this.submitting.set(true);
    this.http.post(`${environment.apiBaseUrl}/api/cashier/orders/${orderId}/paymongo/confirm`, {
      intentId: intent,
      amount: amount ? Number(amount) : null,
      paymentMethod: paymentMethod || null
    }).subscribe({
      next: () => {
        this.submitting.set(false);
        this.router.navigate(['/app/orders', orderId]);
      },
      error: (e) => {
        this.submitting.set(false);
        this.router.navigate(['/app/orders', orderId], {
          queryParams: { paymentError: e?.error?.message || 'confirm_failed' }
        });
      }
    });
  }

  loadPackages(orderIdToEdit?: string | null): void {
    this.http.get<PackageDef[]>(`${environment.apiBaseUrl}/api/cashier/packages`).subscribe({
      next: (p) => {
        this.packages.set(p);
        if (orderIdToEdit) this.loadOrderForEdit(orderIdToEdit);
      },
      error: () => this.packages.set([])
    });
  }

  loadDiscountTemplates(): void {
    this.http.get<DiscountTemplate[]>(`${environment.apiBaseUrl}/api/cashier/discount-templates`).subscribe({
      next: (rows) => this.savedTemplates.set(rows.map(r => ({
        id: r.id,
        label: r.name,
        name: r.name,
        amountType: r.amountType,
        percent: r.amountType === 'PERCENT' ? Number(r.percent || 0) : 0,
        fixedAmount: r.amountType === 'FIXED' ? Number(r.fixedAmount || 0) : 0
      }))),
      error: () => this.savedTemplates.set([])
    });
  }

  private loadOrderForEdit(orderId: string): void {
    this.http.get<any>(`${environment.apiBaseUrl}/api/cashier/orders/${orderId}`).subscribe({
      next: (o) => {
        if (o.status !== 'OPEN') {
          this.error.set('This order must be re-opened before it can be edited.');
          return;
        }
        this.editingOrderId.set(o.id);
        this.transactorName = o.transactorName || o.clientNickname || '';
        if (o.clientId) {
          this.selectedClient.set({ id: o.clientId, nickname: o.clientNickname || this.transactorName });
          this.clientLocked.set(true);
        } else {
          this.selectedClient.set(null);
          this.clientLocked.set(false);
        }
        this.referenceNumber = o.referenceNumber || '';
        this.orNumber = o.orNumber || '';
        this.notes = o.notes || '';
        if (o.scheduledAt) {
          const parts = new Intl.DateTimeFormat('en-CA', {
            timeZone: 'Asia/Manila', year: 'numeric', month: '2-digit', day: '2-digit',
            hour: '2-digit', minute: '2-digit', hour12: false
          }).formatToParts(new Date(o.scheduledAt));
          const lookup = (t: string) => parts.find(p => p.type === t)?.value ?? '';
          this.scheduleDate = `${lookup('year')}-${lookup('month')}-${lookup('day')}`;
          this.scheduleTime = `${lookup('hour')}:${lookup('minute')}`;
        }
        this.weekend.set(!!o.weekend);
        if (o.discount && o.discount > 0) {
          this.discountSummary.set([{ name: 'Existing discount', amount: Number(o.discount) }]);
        }
        const items: CartItem[] = (o.items || []).map((it: any) => {
          const pkg = this.packages().find(p => p.id === it.packageId);
          const attendees: CartAttendee[] = (it.attendees || []).map((a: any) => ({
            serviceId: a.serviceId ?? null,
            packageTierId: a.packageTierId ?? null,
            serviceName: a.serviceName || '',
            lockerNumber: a.lockerNumber || '',
            clientGender: (a.clientGender === 'M' ? 'M' : 'F') as 'M' | 'F'
          }));
          return {
            packageId: it.packageId,
            packageCode: pkg?.code || '',
            packageName: it.packageName || pkg?.name || '',
            includesMassage: pkg?.includesMassage ?? false,
            requiresVipRoom: pkg?.requiresVipRoom ?? false,
            couple: pkg?.couple ?? false,
            defaultPax: pkg?.defaultPax ?? 1,
            tiers: pkg?.tiers || [],
            inclusions: pkg?.inclusions || [],
            roomType: (pkg?.requiresVipRoom ? 'VIP' : (it.roomType === 'PRIVATE' ? 'PRIVATE' : 'COMMON')) as RoomType,
            unitPrice: Number(it.unitPrice || 0),
            lineTotal: Number(it.lineTotal || 0),
            attendees: attendees.length > 0 ? attendees : [this.blankAttendee()]
          };
        });
        this.cart.set(items);
      },
      error: () => this.error.set('Could not load the order for editing.')
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
    const previous = this.selectedClient();
    this.selectedClient.set(c);
    this.searchResults.set([]);
    this.searchTerm = '';
    if (!previous || previous.id !== c.id) {
      this.transactorName = c.nickname;
      this.applyClientGenderToFirstAttendee(c.gender);
    }
  }

  clearClient(): void {
    if (this.clientLocked()) return;
    this.selectedClient.set(null);
    this.transactorName = '';
  }

  private applyClientGenderToFirstAttendee(gender: string | null | undefined): void {
    if (gender !== 'M' && gender !== 'F') return;
    this.cart.update(items => {
      if (items.length === 0 || items[0].attendees.length === 0) return items;
      const first = { ...items[0] };
      const attendees = first.attendees.slice();
      attendees[0] = { ...attendees[0], clientGender: gender };
      first.attendees = attendees;
      return [first, ...items.slice(1)];
    });
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
    if (!this.newClient.email.trim()) {
      this.registerError.set('Email is required.');
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

  private blankAttendee(): CartAttendee {
    return { serviceId: null, packageTierId: null, serviceName: '', lockerNumber: '', clientGender: 'F', discount: 0, providedTsn: null };
  }

  addPackage(): void {
    if (this.selectedPackageId == null) return;
    const pkg = this.packages().find(p => p.id === Number(this.selectedPackageId));
    if (!pkg) return;
    const pax = Math.max(1, pkg.defaultPax || 1);
    const attendees: CartAttendee[] = [];
    for (let i = 0; i < pax; i++) attendees.push(this.blankAttendee());
    const item: CartItem = {
      packageId: pkg.id,
      packageCode: pkg.code,
      packageName: pkg.name,
      includesMassage: pkg.includesMassage,
      requiresVipRoom: pkg.requiresVipRoom,
      couple: pkg.couple,
      defaultPax: pax,
      tiers: pkg.tiers,
      inclusions: pkg.inclusions || [],
      roomType: pkg.requiresVipRoom ? 'VIP' : 'COMMON',
      unitPrice: 0,
      lineTotal: 0,
      attendees
    };
    if (pkg.tiers.length > 0) {
      const tier = pkg.tiers[0];
      const price = this.weekend() ? tier.weekendPrice : tier.weekdayPrice;
      for (const a of item.attendees) {
        a.packageTierId = tier.id;
        a.serviceId = tier.serviceId;
        a.serviceName = tier.serviceName || '';
      }
      item.unitPrice = price;
      item.lineTotal = price;
    }
    const client = this.selectedClient();
    if (this.cart().length === 0 && client && (client.gender === 'M' || client.gender === 'F')) {
      item.attendees[0].clientGender = client.gender as 'M' | 'F';
    }
    this.cart.update(items => [...items, item]);
    this.selectedPackageId = null;
  }

  removeItem(idx: number): void {
    this.cart.update(items => items.filter((_, i) => i !== idx));
  }

  onAttendeeMassageChange(itemIdx: number, attIdx: number, tierId: number | null): void {
    const tierIdNum = tierId != null ? Number(tierId) : null;
    this.cart.update(items => items.map((it, i) => {
      if (i !== itemIdx) return it;
      const tier = tierIdNum != null ? it.tiers.find(t => t.id === tierIdNum) : null;
      const updatedAttendees = it.attendees.map((a, j) => {
        if ((it.couple || it.requiresVipRoom) && j !== attIdx) {
          return {
            ...a,
            packageTierId: tier?.id ?? null,
            serviceId: tier?.serviceId ?? null,
            serviceName: tier?.serviceName ?? ''
          };
        }
        if (j !== attIdx) return a;
        return {
          ...a,
          packageTierId: tier?.id ?? null,
          serviceId: tier?.serviceId ?? null,
          serviceName: tier?.serviceName ?? ''
        };
      });
      const priced = it.tiers.find(t => t.id === (updatedAttendees[0]?.packageTierId ?? null));
      const unit = priced ? (this.weekend() ? priced.weekendPrice : priced.weekdayPrice) : it.unitPrice;
      return { ...it, attendees: updatedAttendees, unitPrice: unit, lineTotal: unit };
    }));
  }

  toggleWeekend(): void {
    this.weekend.update(v => !v);
    this.recomputePrices();
  }

  private recomputePrices(): void {
    this.cart.update(items => items.map(it => {
      const tier = it.tiers.find(t => t.id === (it.attendees[0]?.packageTierId ?? null));
      const unit = tier ? (this.weekend() ? tier.weekendPrice : tier.weekdayPrice) : it.unitPrice;
      return { ...it, unitPrice: unit, lineTotal: unit };
    }));
  }

  setItemRoom(idx: number, rt: RoomType): void {
    this.cart.update(items => items.map((it, i) => {
      if (i !== idx || it.requiresVipRoom) return it;
      return { ...it, roomType: rt };
    }));
  }

  applyVoucher(): void {
    this.voucherError.set(null);
    if (this.hasManualDiscount()) {
      this.voucherError.set('Remove the manual discount first. A voucher and a manual discount cannot be combined.');
      return;
    }
    if (this.hasVoucherApplied()) {
      this.voucherError.set('A voucher is already applied to this order.');
      return;
    }
    const code = this.voucherCode.trim();
    if (!code) {
      this.voucherError.set('Enter a voucher code.');
      return;
    }
    this.http.get<{ id: string; code: string; name: string | null; discount: number }>(
      `${environment.apiBaseUrl}/api/vouchers/check?code=${encodeURIComponent(code)}&subtotal=${this.subtotal()}`
    ).subscribe({
      next: (v) => {
        if (!v || !v.discount || v.discount <= 0) {
          this.voucherError.set('Voucher is not valid or has no discount.');
          return;
        }
        this.voucherId.set(v.id);
        const label = v.name ? `Voucher (${v.name})` : `Voucher ${v.code}`;
        this.discountSummary.update(list => [...list, { name: label, amount: Number(v.discount) }]);
        this.voucherCode = '';
      },
      error: (err) => this.voucherError.set(err?.error?.message || 'Voucher invalid or already redeemed.')
    });
  }

  openDiscountModal(): void {
    if (this.hasVoucherApplied()) {
      this.error.set('Remove the applied voucher before adding a manual discount.');
      return;
    }
    const cfg = this.discountConfig();
    cfg.appliedItemIndices = this.cart().map((_, i) => i);
    this.discountConfig.set({ ...cfg });
    this.showDiscountModal.set(true);
  }

  closeDiscountModal(): void {
    this.showDiscountModal.set(false);
  }

  applyTemplate(tmpl: DiscountTemplate): void {
    this.activeTemplate.set(tmpl.label);
    this.discountConfig.update(cfg => ({
      ...cfg,
      name: tmpl.name || tmpl.label,
      amountType: (tmpl.amountType || 'PERCENT'),
      percent: tmpl.percent,
      fixedAmount: tmpl.fixedAmount || 0,
      type: 'DISCOUNT' as const
    }));
  }

  saveAsTemplate(): void {
    const cfg = this.discountConfig();
    const name = cfg.name?.trim();
    if (!name) {
      this.error.set('Enter a name before saving the discount as a template.');
      return;
    }
    this.http.post<DiscountTemplate>(`${environment.apiBaseUrl}/api/cashier/discount-templates`, {
      name,
      amountType: cfg.amountType,
      percent: cfg.amountType === 'PERCENT' ? cfg.percent : null,
      fixedAmount: cfg.amountType === 'FIXED' ? cfg.fixedAmount : null
    }).subscribe({
      next: () => this.loadDiscountTemplates(),
      error: (err) => this.error.set(err?.error?.message || 'Could not save discount template.')
    });
  }

  deleteTemplate(tmpl: DiscountTemplate): void {
    if (!tmpl.id) return;
    this.confirmService.confirm({
      title: 'Delete discount template',
      message: `Delete template "${tmpl.name || tmpl.label}"?`,
      confirmText: 'Delete',
      cancelText: 'Cancel',
      danger: true
    }).then(ok => {
      if (!ok) return;
      this.http.delete(`${environment.apiBaseUrl}/api/cashier/discount-templates/${tmpl.id}`).subscribe({
        next: () => {
          this.savedTemplates.update(list => list.filter(t => t.id !== tmpl.id));
          if (this.activeTemplate() === tmpl.name) this.activeTemplate.set('Custom');
        },
        error: () => this.error.set('Could not delete template.')
      });
    });
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
          totalDiscountAmt += item.lineTotal * (cfg.percent / 100);
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
    const removed = this.discountSummary()[idx];
    this.discountSummary.update(list => list.filter((_, i) => i !== idx));
    if (removed && removed.name.startsWith('Voucher ')) this.voucherId.set(null);
  }

  removeAllDiscounts(): void {
    this.discountSummary.set([]);
    this.activeTemplate.set('Custom');
    this.voucherId.set(null);
  }

  setPaymentMethod(method: string): void {
    this.paymentMethod.set(method);
  }

  async addPayment(): Promise<void> {
    if (this.total() === 0) {
      this.error.set('No payment is required for a zero-total order.');
      return;
    }
    const amt = Number(this.paymentAmount || 0);
    if (amt <= 0) {
      this.error.set('Enter a payment amount greater than zero.');
      return;
    }
    if (this.paid() + amt > this.total()) {
      this.error.set('Payment would exceed the total amount due. Maximum: ' + (this.total() - this.paid()).toFixed(2));
      return;
    }

    const method = this.paymentMethod();
    let paymentDetails: PaymentDetails | undefined;
    if (method === 'GCASH') {
      const captured = await this.paymentDetailsService.open(method, amt);
      if (!captured) return;
      paymentDetails = captured;
    }

    this.error.set(null);
    this.payments.update(p => [...p, {
      paymentMethod: method,
      amount: amt,
      referenceNumber: this.paymentRef || undefined,
      paymentDetails
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

  get scheduleMinDate(): string {
    return manilaToday();
  }

  get scheduleMinTime(): string | null {
    return this.scheduleDate === manilaToday() ? manilaNowTime() : null;
  }

  private validate(): string | null {
    if (!this.transactorName.trim()) return 'Enter a transactor name.';
    if (!this.scheduleDate || !this.scheduleTime) {
      return 'Enter the schedule date and time.';
    }
    if (this.cart().length === 0) return 'Add at least one package.';
    for (let i = 0; i < this.cart().length; i++) {
      const it = this.cart()[i];
      for (let j = 0; j < it.attendees.length; j++) {
        const a = it.attendees[j];
        if (a.serviceId == null || a.packageTierId == null) {
          return `Item ${i + 1} (${it.packageName}), guest ${j + 1}: choose a massage.`;
        }
        if (!a.lockerNumber.trim()) return `Item ${i + 1} (${it.packageName}), guest ${j + 1}: enter a locker number.`;
        if (!a.clientGender) return `Item ${i + 1} (${it.packageName}), guest ${j + 1}: choose a sex.`;
      }
    }
    return null;
  }

  private buildPayload(): any {
    const first = this.cart()[0];
    const payments = this.payments();
    const isEdit = !!this.editingOrderId();
    const firstPayment = isEdit
      ? null
      : (payments.length > 0 ? payments[0] : null);
    return {
      clientId: this.selectedClient()?.id || null,
      clientNickname: this.transactorName,
      clientGender: first.attendees[0].clientGender,
      scheduledAt: toManilaIso(this.scheduleDate, this.scheduleTime),
      transactorName: this.transactorName,
      groupBooking: this.groupBooking(),
      weekend: this.weekend(),
      pax: this.totalAttendees(),
      lockerNumber: first.attendees[0].lockerNumber || null,
      referenceNumber: this.referenceNumber || null,
      orNumber: this.orNumber || null,
      tsNumber: this.tsNumber || null,
      notes: this.notes || null,
      voucherId: this.voucherId(),
      subtotal: this.subtotal(),
      discount: this.discountSummary()
        .filter(d => !d.name.startsWith('Voucher '))
        .reduce((sum, d) => sum + d.amount, 0),
      tax: this.tax(),
      total: this.total(),
      items: this.cart().map((c, i) => ({
        packageId: c.packageId,
        quantity: 1,
        unitPrice: c.unitPrice,
        lineTotal: c.lineTotal,
        roomType: c.roomType,
        position: i,
        attendees: c.attendees.map((a, j) => ({
          serviceId: a.serviceId,
          packageTierId: a.packageTierId,
          lockerNumber: a.lockerNumber,
          clientGender: a.clientGender,
          position: j,
          discount: a.discount || 0,
          providedTsn: a.providedTsn || null
        }))
      })),
      initialPayment: firstPayment ? {
        paymentMethod: firstPayment.paymentMethod,
        amount: firstPayment.amount,
        referenceNumber: firstPayment.referenceNumber || null,
        paymentDetails: firstPayment.paymentDetails || null
      } : null
    };
  }

  async checkout(): Promise<void> {
    if (this.submitting()) return;
    const err = this.validate();
    if (err) {
      this.error.set(err);
      return;
    }
    const editId = this.editingOrderId();
    const confirmed = await this.confirmService.confirm({
      title: editId ? 'Save Changes' : 'Confirm Checkout',
      message: `${editId ? 'Update' : 'Process'} an order for ${this.transactorName} totaling P${this.total().toFixed(2)} (${this.totalAttendees()} guest(s))?`,
      confirmText: editId ? 'Save changes' : 'Checkout'
    });
    if (!confirmed) return;

    const queuedPayments = this.payments();
    const gatewayMethods = ['GCASH', 'CREDIT', 'DEBIT'];
    const redirectPayment = queuedPayments.length === 1 && gatewayMethods.includes(queuedPayments[0].paymentMethod)
      ? queuedPayments[0]
      : null;

    this.submitting.set(true);
    this.error.set(null);
    const payload = this.buildPayload();

    if (redirectPayment) {
      const orderPayload = { ...payload, initialPayment: null };
      const request = editId
        ? this.http.put<OrderCreated>(`${environment.apiBaseUrl}/api/cashier/orders/${editId}`, orderPayload)
        : this.http.post<OrderCreated>(`${environment.apiBaseUrl}/api/cashier/orders`, orderPayload);
      request.subscribe({
        next: (order) => this.startPayMongo(order.id, redirectPayment),
        error: (err) => {
          this.submitting.set(false);
          this.error.set(err?.error?.message || (editId ? 'Could not update order.' : 'Could not create order.'));
        }
      });
      return;
    }

    if (editId) {
      this.http.put<OrderCreated>(`${environment.apiBaseUrl}/api/cashier/orders/${editId}`, payload).subscribe({
        next: (order) => {
          const queued = this.payments();
          if (queued.length === 0) {
            this.submitting.set(false);
            this.router.navigate(['/app/orders', order.id]);
            return;
          }
          this.recordExtraPayments(order.id, queued, 0);
        },
        error: (e) => {
          this.submitting.set(false);
          this.error.set(e?.error?.message || 'Could not update order.');
        }
      });
      return;
    }

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
      referenceNumber: p.referenceNumber || null,
      paymentDetails: p.paymentDetails || null
    }).subscribe({
      next: () => this.recordExtraPayments(orderId, list, idx + 1),
      error: () => {
        this.submitting.set(false);
        this.router.navigate(['/app/orders', orderId]);
      }
    });
  }

  private startPayMongo(orderId: string, payment: AddedPayment): void {
    this.http.post<{ status: string; intentId: string; redirectUrl: string | null }>(
      `${environment.apiBaseUrl}/api/cashier/orders/${orderId}/paymongo/initiate`,
      {
        paymentMethod: payment.paymentMethod,
        amount: payment.amount,
        referenceNumber: payment.referenceNumber || null,
        paymentDetails: payment.paymentDetails || null
      }
    ).subscribe({
      next: (res) => {
        if (res.status === 'succeeded') {
          this.submitting.set(false);
          this.router.navigate(['/app/orders', orderId]);
          return;
        }
        const origin = window.location.origin;
        const returnUrl = `${origin}/app/cashier?paymongoReturn=1&orderId=${orderId}`
          + `&intent=${encodeURIComponent(res.intentId)}`
          + `&paymentMethod=${encodeURIComponent(payment.paymentMethod)}`
          + `&amount=${payment.amount}`;
        sessionStorage.setItem('paymongoIntent:' + orderId, res.intentId);
        if (res.intentId.startsWith('mock_')) {
          window.location.href = `${origin}/pay/authorize?intent=${encodeURIComponent(res.intentId)}`
            + `&amount=${payment.amount}`
            + `&method=${encodeURIComponent(payment.paymentMethod)}`
            + `&return=${encodeURIComponent(returnUrl)}`;
        } else if (res.redirectUrl) {
          window.location.href = res.redirectUrl;
        } else {
          this.submitting.set(false);
          this.router.navigate(['/app/orders', orderId]);
        }
      },
      error: (err) => {
        this.submitting.set(false);
        this.error.set(err?.error?.message || 'Could not start the PayMongo payment.');
        this.router.navigate(['/app/orders', orderId], {
          queryParams: { paymentError: err?.error?.message || 'paymongo_failed' }
        });
      }
    });
  }
}
