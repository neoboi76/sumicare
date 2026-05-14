import { ChangeDetectionStrategy, Component, OnInit, inject, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { PaginatorComponent } from '../../../shared/components/paginator/paginator.component';

interface OrderItem {
  id: string;
  packageName: string;
  attendees: { id: string }[];
}

interface Order {
  id: string;
  bookingId: string | null;
  treatmentSlipId: string | null;
  clientNickname: string | null;
  transactorName: string | null;
  cashierDisplayName: string | null;
  orNumber: string | null;
  total: number;
  amountPaid: number;
  balance: number;
  status: string;
  createdAt: string;
  groupBooking: boolean;
  roomType: string | null;
  items: OrderItem[];
}

@Component({
  selector: 'sumi-orders-list',
  standalone: true,
  imports: [DecimalPipe, FormsModule, RouterLink, PaginatorComponent],
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

  currentPage = signal(0);
  pageSize = signal(15);

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

  paginatedOrders = computed(() => {
    const start = this.currentPage() * this.pageSize();
    return this.sortedOrders().slice(start, start + this.pageSize());
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
    this.currentPage.set(0);
    this.load();
  }

  toggleSort(col: string): void {
    if (this.sortColumn() === col) {
      this.sortDirection.set(this.sortDirection() === 'asc' ? 'desc' : 'asc');
    } else {
      this.sortColumn.set(col);
      this.sortDirection.set('asc');
    }
    this.currentPage.set(0);
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
    return new Date(iso).toLocaleString('en-US', {
      year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit', hour12: false
    });
  }

  itemSummary(o: Order): string {
    const items = o.items || [];
    if (items.length === 0) return '—';
    const attendees = items.reduce((sum, it) => sum + (it.attendees ? it.attendees.length : 0), 0);
    const first = items[0].packageName || 'Package';
    const more = items.length > 1 ? ` +${items.length - 1}` : '';
    return `${first}${more} · ${attendees} pax`;
  }

  isReservation(o: Order): boolean {
    return !!o.bookingId && o.status === 'OPEN';
  }
}
