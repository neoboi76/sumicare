import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { environment } from '../../../../environments/environment';

interface ClientLite {
  id: string;
  nickname: string;
  email?: string | null;
  gender?: string | null;
  nationality?: string | null;
}

interface ServiceItem {
  id: number;
  name: string;
  durationMinutes: number;
  price: number;
  vip: boolean;
}

interface CartItem {
  serviceId: number;
  name: string;
  price: number;
  originalPrice: number;
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

  searchTerm = '';
  searchResults = signal<ClientLite[]>([]);
  selectedClient = signal<ClientLite | null>(null);

  services = signal<ServiceItem[]>([]);
  selectedServiceId: number | null = null;
  cart = signal<CartItem[]>([]);

  referenceNumber = '';
  notes = '';
  orNumber = '';
  lockerNumber = '';
  pax = 1;
  clientGender: 'M' | 'F' = 'F';

  paymentMethod = signal<'CASH' | 'GCASH' | 'CREDIT' | 'DEBIT'>('CASH');
  paymentAmount = 0;
  paymentRef = '';
  payments = signal<AddedPayment[]>([]);

  showRegister = signal(false);
  newClient = { nickname: '', email: '', gender: 'F', nationality: '' };
  registerError = signal<string | null>(null);

  // Discount modal
  showDiscountModal = signal(false);
  discountConfig = signal<DiscountConfig>({
    name: '',
    type: 'DISCOUNT',
    amountType: 'PERCENT',
    percent: 0,
    fixedAmount: 0,
    appliedItemIndices: []
  });
  discountTemplates = [
    { label: 'Custom', percent: 0, name: '' },
    { label: 'SENIOR/PWD', percent: 20, name: 'Senior/PWD Discount' }
  ];
  activeTemplate = signal<string>('Custom');
  discountSummary = signal<{ name: string; amount: number }[]>([]);

  submitting = signal(false);
  error = signal<string | null>(null);

  subtotal = computed(() => this.cart().reduce((sum, c) => sum + Number(c.price || 0), 0));
  totalDiscount = computed(() => this.discountSummary().reduce((sum, d) => sum + d.amount, 0));
  total = computed(() => Math.max(0, this.subtotal() - this.totalDiscount()));
  paid = computed(() => this.payments().reduce((sum, p) => sum + Number(p.amount || 0), 0));
  due = computed(() => Math.max(0, this.total() - this.paid()));

  ngOnInit(): void {
    this.loadServices();
    this.searchClients();
  }

  loadServices(): void {
    this.http
      .get<ServiceItem[]>(`${environment.apiBaseUrl}/api/services?activeOnly=true`)
      .subscribe({
        next: (s) => this.services.set(s),
        error: () => this.services.set([])
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
        const msg = err?.error?.message || 'Could not register client. Try again.';
        this.registerError.set(msg);
      }
    });
  }

  addService(): void {
    if (this.selectedServiceId == null) return;
    const s = this.services().find(x => x.id === Number(this.selectedServiceId));
    if (!s) return;
    this.cart.update(items => [...items, {
      serviceId: s.id,
      name: s.name,
      price: Number(s.price || 0),
      originalPrice: Number(s.price || 0)
    }]);
    this.selectedServiceId = null;
  }

  removeItem(idx: number): void {
    this.cart.update(items => items.filter((_, i) => i !== idx));
    // Re-apply any active discount
    this.recalcDiscount();
  }

  // --- Discount Modal ---
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
    let totalDiscount = 0;

    if (cfg.type === 'DISCOUNT') {
      for (const idx of cfg.appliedItemIndices) {
        if (idx >= items.length) continue;
        const item = items[idx];
        if (cfg.amountType === 'PERCENT') {
          totalDiscount += item.originalPrice * (cfg.percent / 100);
        } else {
          totalDiscount += cfg.fixedAmount;
        }
      }
    }

    const name = cfg.name || (cfg.amountType === 'PERCENT' ? `${cfg.percent}% discount` : `₱${cfg.fixedAmount} discount`);
    this.discountSummary.set(totalDiscount > 0 ? [{ name, amount: totalDiscount }] : []);
    this.showDiscountModal.set(false);
  }

  removeDiscount(): void {
    this.discountSummary.set([]);
    this.discountConfig.update(cfg => ({
      ...cfg,
      name: '',
      percent: 0,
      fixedAmount: 0,
      appliedItemIndices: []
    }));
    this.activeTemplate.set('Custom');
  }

  private recalcDiscount(): void {
    if (this.discountSummary().length > 0) {
      this.applyDiscount();
    }
  }

  // --- Payments ---
  setPaymentMethod(method: 'CASH' | 'GCASH' | 'CREDIT' | 'DEBIT'): void {
    this.paymentMethod.set(method);
  }

  addPayment(): void {
    const amt = Number(this.paymentAmount || 0);
    if (amt <= 0) return;
    this.payments.update(p => [...p, {
      paymentMethod: this.paymentMethod(),
      amount: amt,
      referenceNumber: this.paymentRef || undefined
    }]);
    this.paymentAmount = 0;
    this.paymentRef = '';
  }

  removePayment(idx: number): void {
    this.payments.update(p => p.filter((_, i) => i !== idx));
  }

  checkout(): void {
    if (!this.selectedClient()) {
      this.error.set('Select a client first.');
      return;
    }
    if (this.cart().length === 0) {
      this.error.set('Add at least one service.');
      return;
    }
    if (this.submitting()) return;

    this.submitting.set(true);
    this.error.set(null);

    const firstPayment = this.payments().length > 0 ? this.payments()[0] : null;
    const payload = {
      clientId: this.selectedClient()?.id,
      clientNickname: this.selectedClient()?.nickname,
      clientGender: this.clientGender,
      pax: this.pax,
      lockerNumber: this.lockerNumber || null,
      serviceIds: this.cart().map(c => c.serviceId),
      referenceNumber: this.referenceNumber || null,
      notes: this.notes || null,
      orNumber: this.orNumber || null,
      subtotal: this.subtotal(),
      discount: this.totalDiscount(),
      total: this.total(),
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
          // record any additional payments after the first
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
