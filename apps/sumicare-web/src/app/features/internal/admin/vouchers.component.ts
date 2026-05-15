import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { DecimalPipe } from '@angular/common';
import { environment } from '../../../../environments/environment';

interface Voucher {
  id: string;
  code: string;
  name: string | null;
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
  editingVoucher = signal<Voucher | null>(null);

  formCode = '';
  formName = '';
  formAmount: number | null = null;
  formPercent: number | null = null;
  formFrom = '';
  formUntil = '';
  formActive = true;

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
      name: this.formName || null,
      discountAmount: this.formAmount ? Number(this.formAmount) : null,
      discountPercent: this.formPercent ? Number(this.formPercent) : null,
      validFrom: this.formFrom || null,
      validUntil: this.formUntil || null,
      active: this.formActive
    };
    const editing = this.editingVoucher();
    const req = editing
      ? this.http.put<Voucher>(`${environment.apiBaseUrl}/api/vouchers/${editing.id}`, payload)
      : this.http.post<Voucher>(`${environment.apiBaseUrl}/api/vouchers`, payload);
    req.subscribe({
      next: () => {
        this.closeForm();
        this.reload();
      }
    });
  }

  startEdit(v: Voucher): void {
    this.editingVoucher.set(v);
    this.formCode = v.code;
    this.formName = v.name || '';
    this.formAmount = v.discountAmount;
    this.formPercent = v.discountPercent;
    this.formFrom = v.validFrom || '';
    this.formUntil = v.validUntil || '';
    this.formActive = v.active;
    this.showForm.set(true);
  }

  closeForm(): void {
    this.showForm.set(false);
    this.editingVoucher.set(null);
    this.formCode = '';
    this.formName = '';
    this.formAmount = null;
    this.formPercent = null;
    this.formFrom = '';
    this.formUntil = '';
    this.formActive = true;
  }
}
