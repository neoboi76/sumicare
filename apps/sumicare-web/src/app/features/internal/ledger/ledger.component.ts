import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { environment } from '../../../../environments/environment';

interface LedgerEntry {
  id: number;
  organizationId: string;
  transactionId: string;
  entryType: string;
  amount: number;
  recordedAt: string;
  metadata: string | null;
}

@Component({
  selector: 'sumi-ledger',
  templateUrl: './ledger.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule]
})
export class LedgerComponent implements OnInit {
  private http = inject(HttpClient);

  entries = signal<LedgerEntry[]>([]);
  loading = signal(false);
  fromDate = signal(new Date().toISOString().slice(0, 10));
  toDate = signal(new Date().toISOString().slice(0, 10));
  typeFilter = signal('');
  total = signal(0);

  entryTypes = ['', 'PAYMENT_RECEIVED', 'REFUND', 'COMMISSION', 'ADJUSTMENT'];

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    const from = `${this.fromDate()}T00:00:00Z`;
    const to = `${this.toDate()}T23:59:59Z`;
    let url = `${environment.apiBaseUrl}/api/pos/ledger?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`;
    if (this.typeFilter()) url += `&type=${this.typeFilter()}`;
    this.http.get<LedgerEntry[]>(url).subscribe({
      next: (e) => {
        this.entries.set(e);
        this.total.set(e.reduce((sum, x) => sum + x.amount, 0));
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  formatCurrency(amount: number): string {
    return new Intl.NumberFormat('en-PH', { style: 'currency', currency: 'PHP' }).format(amount);
  }

  formatDateTime(iso: string): string {
    return new Date(iso).toLocaleString('en-PH', { dateStyle: 'short', timeStyle: 'short' });
  }
}
