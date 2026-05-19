import { ChangeDetectionStrategy, Component, OnInit, inject, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
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
  status: string;
}

interface Balance {
  inflow: number;
  outflow: number;
  balance: number;
  count: number;
}

interface LedgerAccount {
  method: string;
  shortName: string;
  type: string;
  balance: number;
}

@Component({
  selector: 'sumi-ledger',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './ledger.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LedgerComponent implements OnInit {
  private http = inject(HttpClient);

  readonly methods = ['CASH', 'GCASH', 'CREDIT', 'DEBIT'] as const;

  entries = signal<LedgerEntry[]>([]);
  balance = signal<Balance>({ inflow: 0, outflow: 0, balance: 0, count: 0 });
  loading = signal(false);
  fromDate = signal(new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Manila' }).format(new Date()));
  toDate = signal(new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Manila' }).format(new Date()));
  selectedMethod = signal<string | null>(null);
  searchQuery = signal('');
  accountBalances = signal<Map<string, number>>(new Map());

  accounts = computed<LedgerAccount[]>(() => {
    const bals = this.accountBalances();
    return this.methods.map(m => ({
      method: m,
      shortName: m,
      type: m === 'CASH' || m === 'GCASH' ? 'CASH BOOK' : 'DEBT',
      balance: bals.get(m) ?? 0
    }));
  });

  filteredAccounts = computed(() => {
    const q = this.searchQuery().toLowerCase();
    if (!q) return this.accounts();
    return this.accounts().filter(a => a.method.toLowerCase().includes(q));
  });

  selectedAccount = computed(() => {
    const m = this.selectedMethod();
    if (!m) return null;
    return this.accounts().find(a => a.method === m) ?? null;
  });

  ngOnInit(): void {
    this.loadAccountBalances();
  }

  private loadAccountBalances(): void {
    const from = this.fromDate();
    const to = this.toDate();
    for (const m of this.methods) {
      const url = `${environment.apiBaseUrl}/api/cashier/ledger/balance?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}&method=${m}`;
      this.http.get<Balance>(url).subscribe({
        next: (b) => {
          this.accountBalances.update(map => {
            const newMap = new Map(map);
            newMap.set(m, b.balance);
            return newMap;
          });
        }
      });
    }
  }

  selectAccount(method: string): void {
    this.selectedMethod.set(method);
    this.load();
  }

  backToList(): void {
    this.selectedMethod.set(null);
  }

  load(): void {
    const m = this.selectedMethod();
    if (!m) return;
    this.loading.set(true);
    const from = this.fromDate();
    const to = this.toDate();
    const url = `${environment.apiBaseUrl}/api/cashier/ledger?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}&method=${m}`;
    this.http.get<LedgerEntry[]>(url).subscribe({
      next: (e) => { this.entries.set(e); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
    const balUrl = `${environment.apiBaseUrl}/api/cashier/ledger/balance?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}&method=${m}`;
    this.http.get<Balance>(balUrl).subscribe({
      next: (b) => this.balance.set(b),
      error: () => this.balance.set({ inflow: 0, outflow: 0, balance: 0, count: 0 })
    });
  }

  exportCsv(): void {
    const m = this.selectedMethod();
    if (!m) return;
    const from = this.fromDate();
    const to = this.toDate();
    const url = `${environment.apiBaseUrl}/api/cashier/ledger/export.csv?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}&method=${m}`;
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
    return new Date(iso).toLocaleString('en-PH', { dateStyle: 'medium', timeStyle: 'short', hour12: false, hourCycle: 'h23' });
  }

  statusClass(status: string): string {
    switch (status) {
      case 'REFUND': return 'bg-amber-100 text-amber-700';
      case 'REVERSED': return 'bg-rose-100 text-rose-700';
      default: return 'bg-emerald-100 text-emerald-700';
    }
  }

  statusLabel(status: string): string {
    switch (status) {
      case 'REFUND': return 'Refund';
      case 'REVERSED': return 'Reversed';
      default: return 'Completed';
    }
  }

  formatCurrency(n: number): string {
    const abs = Math.abs(n);
    const formatted = abs.toLocaleString('en-PH', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    return n < 0 ? `-\u20B1${formatted}` : `\u20B1${formatted}`;
  }
}
