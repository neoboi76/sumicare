import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { DecimalPipe } from '@angular/common';
import { environment } from '../../../../environments/environment';

interface Service {
  id: number;
  code: string;
  name: string;
  durationMinutes: number;
  commissionAmount: number;
  price: number;
  category: string | null;
  requiresTwoTherapists: boolean;
  fixedRate: boolean;
  vip: boolean;
  active: boolean;
}

@Component({
  selector: 'sumi-admin-services',
  standalone: true,
  imports: [FormsModule, DecimalPipe],
  templateUrl: './services.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ServicesAdminComponent implements OnInit {
  private http = inject(HttpClient);
  services = signal<Service[]>([]);
  showForm = signal(false);

  formCode = '';
  formName = '';
  formDuration = 60;
  formCategory = 'oil';
  formPrice = 500;
  formCommission = 120;
  formFixed = false;
  formTandem = false;
  formVip = false;

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.http.get<Service[]>(`${environment.apiBaseUrl}/api/services`).subscribe({
      next: (s) => this.services.set(s)
    });
  }

  submit(): void {
    const payload = {
      code: this.formCode,
      name: this.formName,
      durationMinutes: Number(this.formDuration),
      category: this.formCategory,
      price: Number(this.formPrice),
      commissionAmount: Number(this.formCommission),
      fixedRate: this.formFixed,
      requiresTwoTherapists: this.formTandem,
      vip: this.formVip
    };
    this.http.post(`${environment.apiBaseUrl}/api/admin/services`, payload).subscribe({
      next: () => {
        this.showForm.set(false);
        this.formCode = '';
        this.formName = '';
        this.reload();
      }
    });
  }

  deactivate(s: Service): void {
    if (!window.confirm(`Deactivate ${s.name}?`)) return;
    this.http.delete(`${environment.apiBaseUrl}/api/admin/services/${s.id}`).subscribe({
      next: () => this.reload()
    });
  }
}
