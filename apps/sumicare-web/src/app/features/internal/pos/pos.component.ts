import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

interface PaymentResponse {
  transactionId: string;
  receiptNumber: string;
  subtotal: number;
  discount: number;
  total: number;
  paymentMethod: string;
  processedAt: string;
}

interface VoucherCheck {
  id: string;
  code: string;
  discount: number;
}

interface CashierShift {
  id: string;
  status: string;
  openedAt: string;
  closedAt: string | null;
  openingFloat: number;
  closingTotal: number | null;
  variance: number | null;
}

@Component({
  selector: 'sumi-pos',
  standalone: true,
  imports: [FormsModule, DecimalPipe],
  templateUrl: './pos.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PosComponent implements OnInit {
  private http = inject(HttpClient);

  sessionId = '';
  subtotal = 0;
  manualDiscount = 0;
  voucherCode = '';
  paymentMethod = 'CASH';
  openingFloat = 0;
  closingTotal = 0;

  last = signal<PaymentResponse | null>(null);
  cashierShift = signal<CashierShift | null>(null);
  voucherStatus = signal<{ ok: boolean; message: string } | null>(null);
  voucherDiscount = signal(0);

  totalDue = () => Math.max(0, this.subtotal - this.voucherDiscount() - Number(this.manualDiscount || 0));

  ngOnInit(): void {
    this.loadShift();
  }

  loadShift(): void {
    this.http.get<CashierShift[]>(`${environment.apiBaseUrl}/api/pos/cashier-shifts/mine`).subscribe({
      next: (s) => this.cashierShift.set(s.length > 0 ? s[0] : null),
      error: () => this.cashierShift.set(null)
    });
  }

  openShift(): void {
    const params = `?openingFloat=${Number(this.openingFloat)}`;
    this.http.post<CashierShift>(`${environment.apiBaseUrl}/api/pos/cashier-shifts/open${params}`, {}).subscribe({
      next: (s) => this.cashierShift.set(s)
    });
  }

  closeShift(id: string): void {
    const params = `?closingTotal=${Number(this.closingTotal)}`;
    this.http.post<CashierShift>(`${environment.apiBaseUrl}/api/pos/cashier-shifts/${id}/close${params}`, {}).subscribe({
      next: (s) => this.cashierShift.set(s)
    });
  }

  onSubtotalChange(): void {
    this.voucherStatus.set(null);
    this.voucherDiscount.set(0);
  }

  checkVoucher(): void {
    if (!this.voucherCode || this.subtotal <= 0) return;
    const params = `?code=${encodeURIComponent(this.voucherCode)}&subtotal=${this.subtotal}`;
    this.http.get<VoucherCheck>(`${environment.apiBaseUrl}/api/vouchers/check${params}`).subscribe({
      next: (v) => {
        this.voucherDiscount.set(v.discount);
        this.voucherStatus.set({ ok: true, message: `Voucher applied: -${v.discount}` });
      },
      error: () => {
        this.voucherDiscount.set(0);
        this.voucherStatus.set({ ok: false, message: 'Voucher invalid or already redeemed' });
      }
    });
  }

  submit(event: Event): void {
    event.preventDefault();
    const payload = {
      sessionId: this.sessionId,
      subtotal: Number(this.subtotal),
      discount: Number(this.manualDiscount || 0),
      voucherCode: this.voucherCode || null,
      paymentMethod: this.paymentMethod
    };
    this.http.post<PaymentResponse>(`${environment.apiBaseUrl}/api/pos/payments`, payload).subscribe({
      next: (r) => {
        this.last.set(r);
        this.voucherCode = '';
        this.voucherStatus.set(null);
        this.voucherDiscount.set(0);
        this.manualDiscount = 0;
      }
    });
  }

  formatDate(iso: string | null): string {
    return iso ? new Date(iso).toLocaleString() : '-';
  }
}
