import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { PaginatorComponent } from '../../../shared/components/paginator/paginator.component';

interface RegisteredClient {
  id: string;
  nickname: string;
  email: string | null;
  gender: string | null;
  nationality: string | null;
}

@Component({
  selector: 'sumi-registered-clients',
  standalone: true,
  imports: [FormsModule, PaginatorComponent],
  templateUrl: './registered-clients.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RegisteredClientsComponent implements OnInit {
  private http = inject(HttpClient);

  clients = signal<RegisteredClient[]>([]);
  loading = signal(false);
  searchTerm = '';
  currentPage = signal(0);
  pageSize = signal(15);

  paginatedClients = computed(() => {
    const start = this.currentPage() * this.pageSize();
    return this.clients().slice(start, start + this.pageSize());
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    const q = this.searchTerm ? `?q=${encodeURIComponent(this.searchTerm)}` : '';
    this.http.get<RegisteredClient[]>(`${environment.apiBaseUrl}/api/clients${q}`).subscribe({
      next: (c) => { this.clients.set(c); this.currentPage.set(0); this.loading.set(false); },
      error: () => { this.clients.set([]); this.loading.set(false); }
    });
  }

  genderLabel(gender: string | null): string {
    if (gender === 'M') return 'Male';
    if (gender === 'F') return 'Female';
    return '-';
  }
}
