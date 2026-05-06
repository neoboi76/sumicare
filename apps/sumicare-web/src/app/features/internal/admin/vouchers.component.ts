import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { DecimalPipe } from '@angular/common';
import { environment } from '../../../../environments/environment';

interface Voucher {
  id: string;
  code: string;
  discountAmount: number | null;
  discountPercent: number | null;
  validFrom: string | null;
  validUntil: string | null;
  redeemedAt: string | null;
  active: boolean;
}

@Component({
  selector: 'sumi-admin-vouchers',
  standalone: true,
  imports: [FormsModule, DecimalPipe],
  templateUrl: './vouchers.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class VouchersAdminComponent implements OnInit {
  private http = inject(HttpClient);
  vouchers = signal<Voucher[]>([]);
  showForm = signal(false);

  formCode = '';
  formAmount: number | null = null;
  formPercent: number | null = null;
  formFrom = '';
  formUntil = '';

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.http.get<Voucher[]>(`${environment.apiBaseUrl}/api/vouchers`).subscribe({
      next: (v) => this.vouchers.set(v)
    });
  }

  status(v: Voucher): string {
    if (v.redeemedAt) return 'Redeemed';
    if (!v.active) return 'Inactive';
    return 'Active';
  }

  submit(): void {
    const payload = {
      code: this.formCode,
      discountAmount: this.formAmount ? Number(this.formAmount) : null,
      discountPercent: this.formPercent ? Number(this.formPercent) : null,
      validFrom: this.formFrom || null,
      validUntil: this.formUntil || null
    };
    this.http.post(`${environment.apiBaseUrl}/api/vouchers`, payload).subscribe({
      next: () => {
        this.showForm.set(false);
        this.formCode = '';
        this.formAmount = null;
        this.formPercent = null;
        this.reload();
      }
    });
  }
}
