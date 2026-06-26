/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { PaginatorComponent } from '../../../shared/components/paginator/paginator.component';
import { ConfirmService } from '../../../shared/components/confirm-dialog/confirm.service';

interface RegisteredClient {
  id: string;
  nickname: string;
  email: string | null;
  gender: string | null;
  nationality: string | null;
}

interface UsageCount {
  label: string;
  count: number;
}

interface VoucherEligibility {
  code: string;
  name: string | null;
  discount: number;
  eligible: boolean;
}

interface ClientUsage {
  clientId: string;
  nickname: string;
  email: string | null;
  bookingCount: number;
  totalSpending: number;
  topServices: UsageCount[];
  topPackages: UsageCount[];
  topTherapists: UsageCount[];
  vouchers: VoucherEligibility[];
}

@Component({
  selector: 'sumi-registered-clients',
  standalone: true,
  imports: [FormsModule, PaginatorComponent, DecimalPipe],
  templateUrl: './registered-clients.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RegisteredClientsComponent implements OnInit {
  private http = inject(HttpClient);
  private confirmService = inject(ConfirmService);

  clients = signal<RegisteredClient[]>([]);
  loading = signal(false);
  searchTerm = signal('');
  currentPage = signal(0);
  pageSize = signal(15);

  showAdd = signal(false);
  saving = signal(false);
  addError = signal<string | null>(null);
  newClient = { nickname: '', email: '', gender: 'F', nationality: '' };

  editClient = signal<RegisteredClient | null>(null);
  editError = signal<string | null>(null);
  editForm = { nickname: '', gender: 'F', nationality: '' };

  detailClient = signal<RegisteredClient | null>(null);
  usage = signal<ClientUsage | null>(null);
  usageLoading = signal(false);

  filteredClients = computed(() => {
    const term = this.searchTerm().trim().toLowerCase();
    if (!term) return this.clients();
    return this.clients().filter(c =>
      [c.nickname, c.email ?? '', c.nationality ?? ''].join(' ').toLowerCase().includes(term)
    );
  });

  paginatedClients = computed(() => {
    const start = this.currentPage() * this.pageSize();
    return this.filteredClients().slice(start, start + this.pageSize());
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.http.get<RegisteredClient[]>(`${environment.apiBaseUrl}/api/clients`).subscribe({
      next: (c) => { this.clients.set(c); this.currentPage.set(0); this.loading.set(false); },
      error: () => { this.clients.set([]); this.loading.set(false); }
    });
  }

  onSearch(value: string): void {
    this.searchTerm.set(value);
    this.currentPage.set(0);
  }

  genderLabel(gender: string | null): string {
    if (gender === 'M') return 'Male';
    if (gender === 'F') return 'Female';
    return '-';
  }

  openAdd(): void {
    this.newClient = { nickname: '', email: '', gender: 'F', nationality: '' };
    this.addError.set(null);
    this.showAdd.set(true);
  }

  closeAdd(): void {
    this.showAdd.set(false);
  }

  submitAdd(): void {
    if (this.saving()) return;
    if (!this.newClient.nickname.trim() || !this.newClient.email.trim()) {
      this.addError.set('Nickname and email are required.');
      return;
    }
    this.saving.set(true);
    this.addError.set(null);
    this.http.post<RegisteredClient>(`${environment.apiBaseUrl}/api/clients`, {
      nickname: this.newClient.nickname.trim(),
      email: this.newClient.email.trim(),
      gender: this.newClient.gender,
      nationality: this.newClient.nationality.trim() || null
    }).subscribe({
      next: (created) => {
        this.clients.update(list => [created, ...list]);
        this.currentPage.set(0);
        this.saving.set(false);
        this.showAdd.set(false);
      },
      error: (err) => {
        this.addError.set(err?.error?.message || 'Could not add client.');
        this.saving.set(false);
      }
    });
  }

  openEdit(client: RegisteredClient): void {
    this.editClient.set(client);
    this.editForm = { nickname: client.nickname, gender: client.gender || 'F', nationality: client.nationality || '' };
    this.editError.set(null);
  }

  closeEdit(): void {
    this.editClient.set(null);
  }

  submitEdit(): void {
    const client = this.editClient();
    if (!client) return;
    if (!this.editForm.nickname.trim()) {
      this.editError.set('Nickname is required.');
      return;
    }
    this.http.patch<RegisteredClient>(`${environment.apiBaseUrl}/api/clients/${client.id}`, {
      nickname: this.editForm.nickname.trim(),
      gender: this.editForm.gender,
      nationality: this.editForm.nationality.trim() || null
    }).subscribe({
      next: (updated) => {
        this.clients.update(list => list.map(c => c.id === updated.id ? updated : c));
        this.editClient.set(null);
      },
      error: (err) => this.editError.set(err?.error?.message || 'Could not save changes.')
    });
  }

  openDetails(client: RegisteredClient): void {
    this.detailClient.set(client);
    this.usage.set(null);
    this.usageLoading.set(true);
    this.http.get<ClientUsage>(`${environment.apiBaseUrl}/api/clients/${client.id}/usage`).subscribe({
      next: (u) => { this.usage.set(u); this.usageLoading.set(false); },
      error: () => this.usageLoading.set(false)
    });
  }

  closeDetails(): void {
    this.detailClient.set(null);
    this.usage.set(null);
  }

  async remove(client: RegisteredClient): Promise<void> {
    const confirmed = await this.confirmService.confirm({
      title: 'Delete client',
      message: `Remove ${client.nickname} from the active client list? Their booking and payment history is preserved.`,
      confirmText: 'Delete',
      danger: true
    });
    if (!confirmed) return;
    this.http.delete(`${environment.apiBaseUrl}/api/clients/${client.id}`).subscribe({
      next: () => this.clients.update(list => list.filter(c => c.id !== client.id)),
      error: () => this.load()
    });
  }
}
