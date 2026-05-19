import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
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
  unitPrice: number;
  lineTotal: number;
  attendees: CartAttendee[];
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
  imports: [FormsModule, DecimalPipe, RouterLink],
  templateUrl: './cashier.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CashierComponent implements OnInit {
  private http = inject(HttpClient);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
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
  orNumber = '';
  tsNumber = '';
  notes = '';

  editingOrderId = signal<string | null>(null);

  voucherCode = '';
  voucherId = signal<string | null>(null);
  voucherError = signal<string | null>(null);

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
  builtinTemplates: DiscountTemplate[] = [
    { label: 'Custom', percent: 0, name: '' },
    { label: 'SENIOR/PWD', percent: 20, name: 'Senior/PWD Discount' }
  ];
  savedTemplates = signal<DiscountTemplate[]>([]);
  discountTemplates = computed<DiscountTemplate[]>(() => [...this.builtinTemplates, ...this.savedTemplates()]);
  activeTemplate = signal<string>('Custom');
  discountSummary = signal<{ name: string; amount: number }[]>([]);

  submitting = signal(false);
  error = signal<string | null>(null);
  tax = signal(0);

  anyVip = computed(() => this.cart().some(c => c.requiresVipRoom));
  effectiveRoomType = computed<RoomType>(() => this.anyVip() ? 'VIP' : this.roomType());
  roomSurcharge = computed(() => this.effectiveRoomType() === 'PRIVATE' ? 500 : 0);

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
    this.loadRoomAvailability();
    this.searchClients();
    this.loadDiscountTemplates();
    const orderId = this.route.snapshot.queryParamMap.get('orderId');
    this.loadPackages(orderId);
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
        this.referenceNumber = o.referenceNumber || '';
        this.orNumber = o.orNumber || '';
        this.notes = o.notes || '';
        this.weekend.set(!!o.weekend);
        this.groupBooking.set(!!o.groupBooking);
        if (o.roomType === 'PRIVATE' || o.roomType === 'COMMON') this.roomType.set(o.roomType);
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
    this.selectedClient.set(c);
    this.searchResults.set([]);
    this.searchTerm = '';
    if (!this.transactorName.trim()) this.transactorName = c.nickname;
    if (c.gender === 'M' || c.gender === 'F') {
      this.cart.update(items => {
        if (items.length === 0 || items[0].attendees.length === 0) return items;
        const first = { ...items[0] };
        const attendees = first.attendees.slice();
        attendees[0] = { ...attendees[0], clientGender: c.gender as 'M' | 'F' };
        first.attendees = attendees;
        return [first, ...items.slice(1)];
      });
    }
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
      unitPrice: 0,
      lineTotal: 0,
      attendees
    };
    if (pkg.tiers.length > 0) {
      const tier = pkg.tiers[0];
      const price = this.weekend() ? tier.weekdayPrice : tier.weekendPrice;
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
    if (this.totalAttendees() > 1) this.groupBooking.set(true);
  }

  removeItem(idx: number): void {
    this.cart.update(items => items.filter((_, i) => i !== idx));
    if (this.totalAttendees() < 2) this.groupBooking.set(false);
  }

  onAttendeeMassageChange(itemIdx: number, attIdx: number, tierId: number | null): void {
    const tierIdNum = tierId != null ? Number(tierId) : null;
    this.cart.update(items => items.map((it, i) => {
      if (i !== itemIdx) return it;
      const tier = tierIdNum != null ? it.tiers.find(t => t.id === tierIdNum) : null;
      const updatedAttendees = it.attendees.map((a, j) => {
        if (it.couple && j > 0) {
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

  setRoomType(rt: RoomType): void {
    if (this.anyVip()) return;
    this.roomType.set(rt);
  }

  applyVoucher(): void {
    const code = this.voucherCode.trim();
    this.voucherError.set(null);
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
    if (!confirm(`Delete template "${tmpl.name || tmpl.label}"?`)) return;
    this.http.delete(`${environment.apiBaseUrl}/api/cashier/discount-templates/${tmpl.id}`).subscribe({
      next: () => {
        this.savedTemplates.update(list => list.filter(t => t.id !== tmpl.id));
        if (this.activeTemplate() === tmpl.name) this.activeTemplate.set('Custom');
      },
      error: () => this.error.set('Could not delete template.')
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

  setPaymentMethod(method: 'CASH' | 'GCASH' | 'CREDIT' | 'DEBIT'): void {
    this.paymentMethod.set(method);
  }

  addPayment(): void {
    const amt = Number(this.paymentAmount || 0);
    if (amt < 0) return;
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
      for (let j = 0; j < it.attendees.length; j++) {
        const a = it.attendees[j];
        if (a.serviceId == null || a.packageTierId == null) {
          return `Item ${i + 1} (${it.packageName}), guest ${j + 1}: choose a massage.`;
        }
        if (!a.lockerNumber.trim()) return `Item ${i + 1} (${it.packageName}), guest ${j + 1}: enter a locker number.`;
        if (!a.clientGender) return `Item ${i + 1} (${it.packageName}), guest ${j + 1}: choose a sex.`;
      }
    }
    if (this.effectiveRoomType() === 'VIP' && !this.anyVip()) {
      return 'VIP room requires a VIP package.';
    }
    return null;
  }

  private buildPayload(): any {
    const first = this.cart()[0];
    const payments = this.payments();
    const isEdit = !!this.editingOrderId();
    const firstPayment = isEdit
      ? null
      : (payments.length > 0
        ? payments[0]
        : (this.total() === 0 ? { paymentMethod: 'CASH', amount: 0, referenceNumber: undefined } : null));
    return {
      clientId: this.selectedClient()?.id || null,
      clientNickname: this.transactorName,
      clientGender: first.attendees[0].clientGender,
      transactorName: this.transactorName,
      groupBooking: this.totalAttendees() > 1 || this.groupBooking(),
      weekend: this.weekend(),
      roomType: this.effectiveRoomType(),
      roomTypeCharge: this.roomSurcharge(),
      pax: this.totalAttendees(),
      lockerNumber: first.attendees[0].lockerNumber || null,
      referenceNumber: this.referenceNumber || null,
      orNumber: this.orNumber || null,
      tsNumber: this.tsNumber || null,
      notes: this.notes || null,
      voucherId: this.voucherId(),
      subtotal: this.subtotal(),
      discount: this.discountSummary().reduce((sum, d) => sum + d.amount, 0),
      tax: this.tax(),
      total: this.total(),
      items: this.cart().map((c, i) => ({
        packageId: c.packageId,
        quantity: 1,
        unitPrice: c.unitPrice,
        lineTotal: c.lineTotal,
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
        referenceNumber: firstPayment.referenceNumber || null
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

    this.submitting.set(true);
    this.error.set(null);
    const payload = this.buildPayload();

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
