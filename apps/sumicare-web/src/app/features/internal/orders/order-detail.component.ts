import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { environment } from '../../../../environments/environment';
import { ConfirmService } from '../../../shared/components/confirm-dialog/confirm.service';
import { PaymentDetailsModalComponent } from '../../../shared/components/payment-details/payment-details-modal.component';
import { PaymentDetails, PaymentDetailsService } from '../../../shared/components/payment-details/payment-details.service';
import { LockerLabelPipe } from '../../../shared/pipes/locker-label.pipe';

interface OrderItemAttendee {
  id: string;
  serviceId: number | null;
  serviceName: string | null;
  packageTierId: number | null;
  lockerNumber: string | null;
  clientGender: string | null;
  sessionId: string | null;
  treatmentSlipId: string | null;
  position: number;
}

interface OrderItem {
  id: string;
  packageId: number;
  packageName: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
  roomType: string | null;
  roomTypeCharge: number;
  position: number;
  attendees: OrderItemAttendee[];
}

interface Order {
  id: string;
  bookingId: string;
  treatmentSlipId: string | null;
  cashierUserId: string | null;
  cashierDisplayName: string | null;
  lastEditedByDisplayName: string | null;
  clientNickname: string | null;
  clientId: string | null;
  serviceName: string | null;
  orNumber: string | null;
  referenceNumber: string | null;
  notes: string | null;
  subtotal: number;
  discount: number;
  tax: number;
  total: number;
  extensionAmount: number;
  extensionMinutes: number;
  amountPaid: number;
  balance: number;
  status: string;
  scheduledAt: string | null;
  createdAt: string;
  completedAt: string | null;
  finishedAt: string | null;
  cancelledAt: string | null;
  cancelledReason: string | null;
  transactorName: string | null;
  groupBooking: boolean;
  weekend: boolean;
  roomType: string | null;
  roomTypeCharge: number;
  sessionCompleted: boolean;
  items: OrderItem[];
}

interface AuditEntry {
  id: number;
  actorUserId: string;
  actionType: string;
  metadata: string | null;
  occurredAt: string;
}

@Component({
  selector: 'sumi-order-detail',
  standalone: true,
  imports: [DecimalPipe, FormsModule, RouterLink, PaymentDetailsModalComponent, LockerLabelPipe],
  templateUrl: './order-detail.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OrderDetailComponent implements OnInit {
  private http = inject(HttpClient);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private confirmService = inject(ConfirmService);
  private paymentDetailsService = inject(PaymentDetailsService);

  order = signal<Order | null>(null);
  audits = signal<AuditEntry[]>([]);
  error = signal<string | null>(null);
  paymentNotice = signal<string | null>(null);
  busy = signal(false);
  loading = signal(true);

  newPaymentMethod: 'CASH' | 'GCASH' | 'CREDIT' | 'DEBIT' = 'CASH';
  newPaymentAmount = 0;
  newPaymentRef = '';
  cancelReason = '';
  showCancel = signal(false);
  refundReason = 'requested_by_customer';
  refundNotes = '';
  showRefund = signal(false);

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) return;
    const params = this.route.snapshot.queryParamMap;
    if (params.get('paymongoReturn')) {
      this.handlePayMongoReturn(id, params.get('intent'), params.get('paymentMethod'), params.get('amount'), params.get('status'));
      return;
    }
    const paymentError = params.get('paymentError');
    if (paymentError) {
      if (paymentError === 'cancelled') {
        this.paymentNotice.set('The PayMongo payment was cancelled. The order is still open and can be paid again.');
      } else if (paymentError === 'confirm_failed' || paymentError === 'paymongo_failed') {
        this.paymentNotice.set('The PayMongo payment could not be completed. The order is still open and can be paid again.');
      } else {
        this.paymentNotice.set('PayMongo: ' + paymentError);
      }
    }
    this.load(id);
  }

  private load(id: string): void {
    this.loading.set(true);
    this.http.get<Order>(`${environment.apiBaseUrl}/api/cashier/orders/${id}`).subscribe({
      next: (o) => {
        this.order.set(o);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Order not found.');
        this.loading.set(false);
      }
    });
    this.http.get<AuditEntry[]>(`${environment.apiBaseUrl}/api/audit-logs/by-target?entity=ORDER&id=${id}`).subscribe({
      next: (a) => this.audits.set(a),
      error: () => this.audits.set([])
    });
  }

  async recordPayment(): Promise<void> {
    const o = this.order();
    if (!o || this.busy()) return;
    if (!this.newPaymentAmount || this.newPaymentAmount <= 0) {
      this.error.set('Enter a valid payment amount.');
      return;
    }
    if (this.newPaymentAmount > o.balance) {
      this.error.set('Payment amount exceeds remaining balance of ' + o.balance.toFixed(2));
      return;
    }
    if (this.newPaymentMethod === 'CASH') {
      this.busy.set(true);
      this.http.post<Order>(`${environment.apiBaseUrl}/api/cashier/orders/${o.id}/payments`, {
        paymentMethod: this.newPaymentMethod,
        amount: this.newPaymentAmount,
        referenceNumber: this.newPaymentRef || null
      }).subscribe({
        next: (updated) => {
          this.order.set(updated);
          this.newPaymentAmount = 0;
          this.newPaymentRef = '';
          this.error.set(null);
          this.busy.set(false);
          this.load(updated.id);
        },
        error: (err) => {
          this.error.set(err?.error?.message || 'Could not record payment.');
          this.busy.set(false);
        }
      });
      return;
    }

    let details: PaymentDetails | null = null;
    if (this.newPaymentMethod === 'GCASH') {
      details = await this.paymentDetailsService.open(this.newPaymentMethod, this.newPaymentAmount);
      if (!details) return;
    }
    this.startPayMongo(o.id, this.newPaymentMethod, this.newPaymentAmount, this.newPaymentRef, details);
  }

  private startPayMongo(orderId: string, method: string, amount: number, reference: string, details: PaymentDetails | null): void {
    this.busy.set(true);
    this.error.set(null);
    this.http.post<{ status: string; intentId: string; redirectUrl: string | null }>(
      `${environment.apiBaseUrl}/api/cashier/orders/${orderId}/paymongo/initiate`,
      {
        paymentMethod: method,
        amount,
        referenceNumber: reference || null,
        paymentDetails: details,
        returnPath: `/app/orders/${orderId}`
      }
    ).subscribe({
      next: (res) => {
        if (res.status === 'succeeded') {
          this.busy.set(false);
          this.load(orderId);
          return;
        }
        const origin = window.location.origin;
        const returnUrl = `${origin}/app/orders/${orderId}?paymongoReturn=1&orderId=${orderId}`
          + `&intent=${encodeURIComponent(res.intentId)}`
          + `&paymentMethod=${encodeURIComponent(method)}`
          + `&amount=${amount}`;
        sessionStorage.setItem('paymongoIntent:' + orderId, res.intentId);
        if (res.intentId && res.intentId.startsWith('mock_')) {
          window.location.href = `${origin}/pay/authorize?intent=${encodeURIComponent(res.intentId)}`
            + `&amount=${amount}`
            + `&method=${encodeURIComponent(method)}`
            + `&return=${encodeURIComponent(returnUrl)}`;
        } else if (res.redirectUrl) {
          window.location.href = res.redirectUrl;
        } else {
          this.busy.set(false);
          this.load(orderId);
        }
      },
      error: (err) => {
        this.error.set(err?.error?.message || 'Could not start the payment.');
        this.busy.set(false);
      }
    });
  }

  private handlePayMongoReturn(orderId: string, intentId: string | null, method: string | null, amount: string | null, status: string | null): void {
    const resolvedIntent = intentId ?? sessionStorage.getItem('paymongoIntent:' + orderId);
    sessionStorage.removeItem('paymongoIntent:' + orderId);
    if (status === 'cancelled' || status === 'failed') {
      this.paymentNotice.set('The PayMongo payment was cancelled. The order is still open and can be paid again.');
      this.load(orderId);
      return;
    }
    if (!resolvedIntent) {
      this.paymentNotice.set('The payment was not completed. The order is still open and can be paid again.');
      this.load(orderId);
      return;
    }
    this.busy.set(true);
    this.http.post<Order>(`${environment.apiBaseUrl}/api/cashier/orders/${orderId}/paymongo/confirm`, {
      intentId: resolvedIntent,
      amount: amount ? Number(amount) : null,
      paymentMethod: method || null
    }).subscribe({
      next: (updated) => {
        this.order.set(updated);
        this.busy.set(false);
        this.paymentNotice.set('Payment recorded.');
        this.load(orderId);
      },
      error: (err) => {
        this.busy.set(false);
        this.paymentNotice.set(err?.error?.message || 'We could not confirm the PayMongo payment. The order is still open.');
        this.load(orderId);
      }
    });
  }

  async markPaid(): Promise<void> {
    const o = this.order();
    if (!o || this.busy()) return;
    const confirmed = await this.confirmService.confirm({
      title: 'Mark as Paid',
      message: 'Are you sure you want to mark this order as fully paid?',
      confirmText: 'Mark Paid'
    });
    if (!confirmed) return;
    this.busy.set(true);
    this.http.post<Order>(`${environment.apiBaseUrl}/api/cashier/orders/${o.id}/mark-paid`, {}).subscribe({
      next: (updated) => {
        this.order.set(updated);
        this.busy.set(false);
        this.load(updated.id);
      },
      error: (err) => {
        this.error.set(err?.error?.message || 'Could not mark as paid.');
        this.busy.set(false);
      }
    });
  }

  async markOpen(): Promise<void> {
    const o = this.order();
    if (!o || this.busy()) return;
    const confirmed = await this.confirmService.confirm({
      title: 'Reopen Order',
      message: 'Are you sure you want to reopen this order?',
      confirmText: 'Reopen'
    });
    if (!confirmed) return;
    this.busy.set(true);
    this.http.post<Order>(`${environment.apiBaseUrl}/api/cashier/orders/${o.id}/open`, {}).subscribe({
      next: (updated) => {
        this.order.set(updated);
        this.busy.set(false);
        this.load(updated.id);
      },
      error: (err) => {
        this.error.set(err?.error?.message || 'Could not open order.');
        this.busy.set(false);
      }
    });
  }

  async cancelPayment(): Promise<void> {
    const o = this.order();
    if (!o || this.busy()) return;
    if (o.status !== 'OPEN') {
      this.error.set('Can only cancel payment on an OPEN order.');
      return;
    }
    const confirmed = await this.confirmService.confirm({
      title: 'Cancel Payment',
      message: 'Are you sure you want to cancel the payment? This will reverse the transaction.',
      confirmText: 'Cancel Payment',
      danger: true
    });
    if (!confirmed) return;
    
    this.busy.set(true);
    this.http.post<Order>(`${environment.apiBaseUrl}/api/cashier/orders/${o.id}/cancel-payment`, {}).subscribe({
      next: (updated) => {
        this.order.set(updated);
        this.busy.set(false);
        this.error.set(null);
        this.load(updated.id);
      },
      error: (err) => {
        this.error.set(err?.error?.message || 'Could not cancel payment.');
        this.busy.set(false);
      }
    });
  }

  openCancel(): void {
    this.cancelReason = '';
    this.showCancel.set(true);
  }

  closeCancel(): void {
    this.showCancel.set(false);
  }

  confirmCancel(): void {
    const o = this.order();
    if (!o) return;
    this.busy.set(true);
    this.http.post<Order>(`${environment.apiBaseUrl}/api/cashier/orders/${o.id}/cancel`, {
      reason: this.cancelReason || null
    }).subscribe({
      next: (updated) => {
        this.order.set(updated);
        this.showCancel.set(false);
        this.busy.set(false);
        this.load(updated.id);
      },
      error: (err) => {
        this.error.set(err?.error?.message || 'Could not cancel order.');
        this.busy.set(false);
      }
    });
  }

  openRefund(): void {
    this.refundReason = 'requested_by_customer';
    this.refundNotes = '';
    this.showRefund.set(true);
  }

  closeRefund(): void {
    this.showRefund.set(false);
  }

  confirmRefund(): void {
    const o = this.order();
    if (!o || this.busy()) return;
    this.busy.set(true);
    this.http.post<Order>(`${environment.apiBaseUrl}/api/cashier/orders/${o.id}/refund`, {
      amount: null,
      reason: this.refundReason,
      notes: this.refundNotes || null
    }).subscribe({
      next: (updated) => {
        this.order.set(updated);
        this.showRefund.set(false);
        this.busy.set(false);
        this.error.set(null);
        this.load(updated.id);
      },
      error: (err) => {
        this.error.set(err?.error?.message || 'Could not refund the order.');
        this.busy.set(false);
      }
    });
  }

  formatDate(iso: string | null): string {
    return iso ? new Date(iso).toLocaleString('en-US', {
      timeZone: 'Asia/Manila',
      year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit', hour12: false
    }) : '\u2014';
  }

  formatSchedule(iso: string | null): string {
    return iso ? new Date(iso).toLocaleString('en-US', {
      timeZone: 'Asia/Manila',
      weekday: 'short', year: 'numeric', month: 'short', day: 'numeric',
      hour: 'numeric', minute: '2-digit', hour12: true
    }) : '\u2014';
  }

  statusClass(status: string): string {
    switch (status) {
      case 'OPEN': return 'bg-amber-100 text-amber-700';
      case 'PAID': return 'bg-emerald-100 text-emerald-700';
      case 'CANCELLED': return 'bg-rose-100 text-rose-700';
      case 'REFUNDED': return 'bg-violet-100 text-violet-700';
      default: return 'bg-slate-100 text-slate-700';
    }
  }

  back(): void {
    this.router.navigate(['/app/orders']);
  }

  editOrder(): void {
    const o = this.order();
    if (!o) return;
    this.router.navigate(['/app/cashier'], { queryParams: { orderId: o.id } });
  }

  downloadReceipt(): void {
    const o = this.order();
    if (!o) return;
    this.http.get(`${environment.apiBaseUrl}/api/cashier/orders/${o.id}/receipt.pdf`, {
      responseType: 'blob' as const,
      observe: 'response' as const
    }).subscribe({
      next: (response) => {
        const blob = response.body;
        if (!blob) {
          this.error.set('No PDF returned.');
          return;
        }
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `receipt-${o.orNumber || o.id}.pdf`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
      },
      error: () => this.error.set('Failed to download receipt PDF.')
    });
  }
}
