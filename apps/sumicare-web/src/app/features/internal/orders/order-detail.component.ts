import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { environment } from '../../../../environments/environment';

interface Order {
  id: string;
  bookingId: string;
  treatmentSlipId: string | null;
  cashierUserId: string | null;
  clientNickname: string | null;
  clientId: string | null;
  serviceName: string | null;
  orNumber: string | null;
  referenceNumber: string | null;
  notes: string | null;
  subtotal: number;
  discount: number;
  total: number;
  amountPaid: number;
  balance: number;
  status: string;
  createdAt: string;
  completedAt: string | null;
  finishedAt: string | null;
  cancelledAt: string | null;
  cancelledReason: string | null;
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
  imports: [DecimalPipe, FormsModule, RouterLink],
  templateUrl: './order-detail.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OrderDetailComponent implements OnInit {
  private http = inject(HttpClient);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  order = signal<Order | null>(null);
  audits = signal<AuditEntry[]>([]);
  error = signal<string | null>(null);
  busy = signal(false);

  newPaymentMethod: 'CASH' | 'GCASH' | 'CREDIT' | 'DEBIT' = 'CASH';
  newPaymentAmount = 0;
  newPaymentRef = '';
  cancelReason = '';
  showCancel = signal(false);

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) return;
    this.load(id);
  }

  private load(id: string): void {
    this.http.get<Order>(`${environment.apiBaseUrl}/api/cashier/orders/${id}`).subscribe({
      next: (o) => this.order.set(o),
      error: () => this.error.set('Order not found.')
    });
    this.http.get<AuditEntry[]>(`${environment.apiBaseUrl}/api/audit-logs/by-target?entity=ORDER&id=${id}`).subscribe({
      next: (a) => this.audits.set(a),
      error: () => this.audits.set([])
    });
  }

  recordPayment(): void {
    const o = this.order();
    if (!o || this.busy()) return;
    if (!this.newPaymentAmount || this.newPaymentAmount <= 0) {
      this.error.set('Enter a payment amount > 0.');
      return;
    }
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
  }

  markPaid(): void {
    const o = this.order();
    if (!o || this.busy()) return;
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

  formatDate(iso: string | null): string {
    return iso ? new Date(iso).toLocaleString() : '—';
  }

  statusClass(status: string): string {
    switch (status) {
      case 'PENDING': return 'bg-slate-200 text-slate-700';
      case 'PAID': return 'bg-emerald-100 text-emerald-700';
      case 'COMPLETED': return 'bg-blue-100 text-blue-700';
      case 'CANCELLED': return 'bg-rose-100 text-rose-700';
      default: return 'bg-slate-100 text-slate-700';
    }
  }

  back(): void {
    this.router.navigate(['/app/orders']);
  }
}
