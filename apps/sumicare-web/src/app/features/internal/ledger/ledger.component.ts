import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { environment } from '../../../../environments/environment';

interface LedgerEntry {
  id: number;
  transactionId: string;
  orderId: string | null;
  orNumber: string | null;
  clientNickname: string | null;
  entryType: string;
  paymentMethod: string | null;
  amount: number;
  recordedAt: string;
  metadata: string | null;
}

interface Balance {
  inflow: number;
  outflow: number;
  balance: number;
  count: number;
}

@Component({
  selector: 'sumi-ledger',
  standalone: true,
  imports: [DecimalPipe, FormsModule, RouterLink],
  templateUrl: './ledger.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LedgerComponent implements OnInit {
  private http = inject(HttpClient);

  readonly methods = ['ALL', 'CASH', 'GCASH', 'CREDIT', 'DEBIT'] as const;

  entries = signal<LedgerEntry[]>([]);
  balance = signal<Balance>({ inflow: 0, outflow: 0, balance: 0, count: 0 });
  loading = signal(false);
  fromDate = signal(new Date().toISOString().slice(0, 10));
  toDate = signal(new Date().toISOString().slice(0, 10));
  activeMethod = signal<typeof this.methods[number]>('ALL');

  ngOnInit(): void { this.load(); }

  setMethod(m: typeof this.methods[number]): void {
    this.activeMethod.set(m);
    this.load();
  }

  load(): void {
    this.loading.set(true);
    const from = `${this.fromDate()}T00:00:00Z`;
    const to = `${this.toDate()}T23:59:59Z`;
    const m = this.activeMethod();
    const methodParam = m === 'ALL' ? '' : `&method=${m}`;
    const url = `${environment.apiBaseUrl}/api/cashier/ledger?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}${methodParam}`;
    this.http.get<LedgerEntry[]>(url).subscribe({
      next: (e) => { this.entries.set(e); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
    const balUrl = `${environment.apiBaseUrl}/api/cashier/ledger/balance?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}${methodParam}`;
    this.http.get<Balance>(balUrl).subscribe({
      next: (b) => this.balance.set(b),
      error: () => this.balance.set({ inflow: 0, outflow: 0, balance: 0, count: 0 })
    });
  }

  exportCsv(): void {
    const from = `${this.fromDate()}T00:00:00Z`;
    const to = `${this.toDate()}T23:59:59Z`;
    const m = this.activeMethod();
    const methodParam = m === 'ALL' ? '' : `&method=${m}`;
    const url = `${environment.apiBaseUrl}/api/cashier/ledger/export.csv?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}${methodParam}`;
    this.http.get(url, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        const objUrl = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = objUrl;
        a.download = `ledger-${m.toLowerCase()}-${this.fromDate()}-to-${this.toDate()}.csv`;
        document.body.appendChild(a); a.click(); document.body.removeChild(a);
        URL.revokeObjectURL(objUrl);
      },
      error: () => alert('Export failed.')
    });
  }

  formatDateTime(iso: string): string {
    return new Date(iso).toLocaleString('en-PH', { dateStyle: 'short', timeStyle: 'short' });
  }
}
