import { ChangeDetectionStrategy, Component, OnInit, inject, signal, computed } from '@angular/core';
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

  sortColumn = signal<string>('createdAt');
  sortDirection = signal<'asc' | 'desc'>('desc');

  sortedOrders = computed(() => {
    const data = [...this.orders()];
    const col = this.sortColumn();
    const dir = this.sortDirection();
    data.sort((a, b) => {
      let valA: any = (a as any)[col];
      let valB: any = (b as any)[col];
      if (typeof valA === 'string') valA = valA.toLowerCase();
      if (typeof valB === 'string') valB = valB.toLowerCase();
      if (valA == null) return 1;
      if (valB == null) return -1;
      if (valA < valB) return dir === 'asc' ? -1 : 1;
      if (valA > valB) return dir === 'asc' ? 1 : -1;
      return 0;
    });
    return data;
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    let statusParam = '';
    const f = this.filter();
    if (f === 'OPEN') statusParam = '?status=OPEN';
    else if (f === 'PAID') statusParam = '?status=PAID';
    else if (f === 'CANCELLED') statusParam = '?status=CANCELLED';
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

  toggleSort(col: string): void {
    if (this.sortColumn() === col) {
      this.sortDirection.set(this.sortDirection() === 'asc' ? 'desc' : 'asc');
    } else {
      this.sortColumn.set(col);
      this.sortDirection.set('asc');
    }
  }

  sortIcon(col: string): string {
    if (this.sortColumn() !== col) return '';
    return this.sortDirection() === 'asc' ? ' ▲' : ' ▼';
  }

  statusClass(status: string): string {
    switch (status) {
      case 'OPEN': return 'bg-amber-100 text-amber-700';
      case 'PAID': return 'bg-emerald-100 text-emerald-700';
      case 'CANCELLED': return 'bg-rose-100 text-rose-700';
      default: return 'bg-slate-100 text-slate-700';
    }
  }

  formatDate(iso: string): string {
    return new Date(iso).toLocaleString();
  }
}
