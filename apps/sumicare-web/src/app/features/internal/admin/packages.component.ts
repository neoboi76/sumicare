import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { ConfirmService } from '../../../shared/components/confirm-dialog/confirm.service';
import { SortableColumnDirective } from '../../../shared/directives/sortable-column.directive';
import { SortIconComponent } from '../../../shared/components/sort-icon/sort-icon.component';
import { SortState, sortRows } from '../../../shared/utils/compare-by';

interface PackageTier {
  id: number | null;
  serviceId: number | null;
  serviceCode: string | null;
  serviceName: string | null;
  weekdayPrice: number;
  weekendPrice: number;
}

interface PackageRow {
  id: number;
  code: string;
  name: string;
  description: string | null;
  benefits: string | null;
  maxStayHours: number | null;
  defaultPax: number;
  couple: boolean;
  includesMassage: boolean;
  bundlesPrivateRoom: boolean;
  requiresVipRoom: boolean;
  active: boolean;
  tiers: PackageTier[];
}

interface ServiceRow {
  id: number;
  code: string;
  name: string;
  price: number;
}

@Component({
  selector: 'sumi-packages-admin',
  standalone: true,
  imports: [CommonModule, FormsModule, SortableColumnDirective, SortIconComponent],
  templateUrl: './packages.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PackagesAdminComponent implements OnInit {
  private http = inject(HttpClient);
  private confirmService = inject(ConfirmService);

  packages = signal<PackageRow[]>([]);
  services = signal<ServiceRow[]>([]);
  error = signal<string | null>(null);
  loading = signal(true);

  editing = signal<PackageRow | null>(null);
  showForm = signal(false);

  sortState = signal<SortState>({ key: 'name', direction: 'asc' });

  sortedPackages = computed(() => {
    const rows = this.packages();
    const state = this.sortState();
    return sortRows(rows, state, (p) => {
      switch (state.key) {
        case 'code': return p.code;
        case 'name': return p.name;
        case 'defaultPax': return p.defaultPax;
        case 'couple': return p.couple ? 1 : 0;
        case 'requiresVipRoom': return p.requiresVipRoom ? 1 : 0;
        case 'active': return p.active ? 1 : 0;
        default: return '';
      }
    });
  });

  ngOnInit(): void {
    this.reload();
    this.http.get<ServiceRow[]>(`${environment.apiBaseUrl}/api/services`).subscribe({
      next: (s) => this.services.set(s),
      error: () => this.services.set([])
    });
  }

  reload(): void {
    this.loading.set(true);
    this.http.get<PackageRow[]>(`${environment.apiBaseUrl}/api/cashier/packages/all`).subscribe({
      next: (rows) => { this.packages.set(rows); this.loading.set(false); },
      error: () => { this.packages.set([]); this.loading.set(false); }
    });
  }

  startCreate(): void {
    this.editing.set({
      id: 0,
      code: '',
      name: '',
      description: null,
      benefits: null,
      maxStayHours: null,
      defaultPax: 1,
      couple: false,
      includesMassage: true,
      bundlesPrivateRoom: false,
      requiresVipRoom: false,
      active: true,
      tiers: []
    });
    this.error.set(null);
    this.showForm.set(true);
  }

  startEdit(row: PackageRow): void {
    this.editing.set({ ...row, tiers: row.tiers.map(t => ({ ...t })) });
    this.error.set(null);
    this.showForm.set(true);
  }

  cancelEdit(): void {
    this.showForm.set(false);
    this.editing.set(null);
  }

  addTier(): void {
    const e = this.editing();
    if (!e) return;
    e.tiers.push({ id: null, serviceId: null, serviceCode: null, serviceName: null, weekdayPrice: 0, weekendPrice: 0 });
    this.editing.set({ ...e });
  }

  removeTier(idx: number): void {
    const e = this.editing();
    if (!e) return;
    e.tiers.splice(idx, 1);
    this.editing.set({ ...e });
  }

  save(): void {
    const e = this.editing();
    if (!e) return;
    if (!e.code.trim() || !e.name.trim()) {
      this.error.set('Code and name are required.');
      return;
    }
    if (e.defaultPax < 1) {
      this.error.set('Default pax must be at least 1.');
      return;
    }
    const payload = {
      code: e.code.trim(),
      name: e.name.trim(),
      description: e.description || null,
      benefits: e.benefits || null,
      maxStayHours: e.maxStayHours,
      defaultPax: e.defaultPax,
      couple: e.couple,
      includesMassage: e.includesMassage,
      bundlesPrivateRoom: e.bundlesPrivateRoom,
      requiresVipRoom: e.requiresVipRoom,
      active: e.active,
      tiers: e.tiers.filter(t => t.serviceId != null).map(t => ({
        serviceId: t.serviceId,
        weekdayPrice: Number(t.weekdayPrice || 0),
        weekendPrice: Number(t.weekendPrice || 0)
      }))
    };
    const url = e.id
      ? `${environment.apiBaseUrl}/api/cashier/packages/${e.id}`
      : `${environment.apiBaseUrl}/api/cashier/packages`;
    const req$ = e.id
      ? this.http.patch<PackageRow>(url, payload)
      : this.http.post<PackageRow>(url, payload);
    req$.subscribe({
      next: () => {
        this.showForm.set(false);
        this.editing.set(null);
        this.reload();
      },
      error: (err) => this.error.set(err?.error?.message || 'Could not save the package.')
    });
  }

  async deactivate(row: PackageRow): Promise<void> {
    const confirmed = await this.confirmService.confirm({
      title: 'Deactivate package',
      message: `Deactivate ${row.name}? It will no longer appear in the cashier or public booking pickers.`,
      confirmText: 'Deactivate',
      danger: true
    });
    if (!confirmed) return;
    this.http.delete(`${environment.apiBaseUrl}/api/cashier/packages/${row.id}`).subscribe({
      next: () => this.reload(),
      error: (err) => this.error.set(err?.error?.message || 'Could not deactivate the package.')
    });
  }

  reactivate(row: PackageRow): void {
    this.http.post(`${environment.apiBaseUrl}/api/cashier/packages/${row.id}/reactivate`, {}).subscribe({
      next: () => this.reload(),
      error: (err) => this.error.set(err?.error?.message || 'Could not reactivate the package.')
    });
  }

  onTierService(tier: PackageTier, serviceId: number | null): void {
    tier.serviceId = serviceId;
    const svc = this.services().find(s => s.id === serviceId);
    if (svc) {
      tier.serviceCode = svc.code;
      tier.serviceName = svc.name;
      if (!tier.weekdayPrice) tier.weekdayPrice = svc.price;
      if (!tier.weekendPrice) tier.weekendPrice = svc.price + 100;
    }
  }
}
