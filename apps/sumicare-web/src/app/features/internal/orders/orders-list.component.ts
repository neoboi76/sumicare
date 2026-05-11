import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { environment } from '../../../../environments/environment';

interface Order {
  id: string;
  bookingId: string;
  treatmentSlipId: string | null;
  clientNickname: string | null;
  orNumber: string | null;
  total: number;
  amountPaid: number;
  balance: number;
  status: string;
  createdAt: string;
}

@Component({
  selector: 'sumi-orders-list',
  standalone: true,
  imports: [DecimalPipe, FormsModule, RouterLink],
  templateUrl: './orders-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OrdersListComponent implements OnInit {
  private http = inject(HttpClient);

  orders = signal<Order[]>([]);
  loading = signal(false);
  filter = signal<string>('PAID');

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    let statusParam = '';
    const f = this.filter();
    if (f === 'PAID') statusParam = '?status=PAID';
    else if (f === 'COMPLETED') statusParam = '?status=COMPLETED';
    else if (f === 'CANCELLED') statusParam = '?status=CANCELLED';
    else if (f === 'PENDING') statusParam = '?status=PENDING';
    else if (f === 'ALL') statusParam = '';
    this.http.get<Order[]>(`${environment.apiBaseUrl}/api/cashier/orders${statusParam}`).subscribe({
      next: (o) => {
        this.orders.set(o);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  setFilter(f: string): void {
    this.filter.set(f);
    this.load();
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

  formatDate(iso: string): string {
    return new Date(iso).toLocaleString();
  }
}
