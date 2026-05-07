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
  description: string | null;
  imageUrl: string | null;
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
  formDescription = '';
  formImageUrl = '';

  editingId = signal<number | null>(null);
  editDescription = '';
  editImageUrl = '';

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
      vip: this.formVip,
      description: this.formDescription || null,
      imageUrl: this.formImageUrl || null
    };
    this.http.post(`${environment.apiBaseUrl}/api/admin/services`, payload).subscribe({
      next: () => {
        this.showForm.set(false);
        this.formCode = '';
        this.formName = '';
        this.formDescription = '';
        this.formImageUrl = '';
        this.reload();
      }
    });
  }

  openEdit(s: Service): void {
    this.editingId.set(s.id);
    this.editDescription = s.description ?? '';
    this.editImageUrl = s.imageUrl ?? '';
  }

  saveMedia(s: Service): void {
    this.http.patch(`${environment.apiBaseUrl}/api/admin/services/${s.id}`, {
      description: this.editDescription || null,
      imageUrl: this.editImageUrl || null
    }).subscribe({ next: () => { this.editingId.set(null); this.reload(); } });
  }

  deactivate(s: Service): void {
    if (!window.confirm(`Deactivate ${s.name}?`)) return;
    this.http.delete(`${environment.apiBaseUrl}/api/admin/services/${s.id}`).subscribe({
      next: () => this.reload()
    });
  }

  exportCsv(): void {
    this.http.get(`${environment.apiBaseUrl}/api/services/export`,
      { responseType: 'blob' }
    ).subscribe(blob => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      const stamp = new Date().toISOString().slice(0, 10);
      a.href = url;
      a.download = `services-catalogue-${stamp}.csv`;
      a.click();
      URL.revokeObjectURL(url);
    });
  }
}
