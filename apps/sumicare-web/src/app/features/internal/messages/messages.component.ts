import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { environment } from '../../../../environments/environment';

interface ContactMessage {
  id: string;
  name: string;
  email: string;
  message: string;
  ipAddress: string | null;
  createdAt: string;
  readAt: string | null;
}

@Component({
  selector: 'sumi-messages',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './messages.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MessagesComponent implements OnInit {
  private http = inject(HttpClient);

  messages = signal<ContactMessage[]>([]);
  filter = signal<'all' | 'unread'>('unread');
  searchTerm = signal('');
  selected = signal<ContactMessage | null>(null);
  loading = signal(false);
  exportFrom = '';
  exportTo = '';
  exporting = signal(false);

  filteredMessages = computed(() => {
    const term = this.searchTerm().trim().toLowerCase();
    if (!term) return this.messages();
    return this.messages().filter(m =>
      [m.name, m.email, m.message].join(' ').toLowerCase().includes(term)
    );
  });

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading.set(true);
    const url = `${environment.apiBaseUrl}/api/contact-messages?unread=${this.filter() === 'unread'}`;
    this.http.get<ContactMessage[]>(url).subscribe({
      next: (m) => { this.messages.set(m); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  setFilter(f: 'all' | 'unread'): void {
    this.filter.set(f);
    this.load();
  }

  open(m: ContactMessage): void {
    this.selected.set(m);
  }

  close(): void {
    this.selected.set(null);
  }

  markRead(m: ContactMessage): void {
    this.http.post<ContactMessage>(`${environment.apiBaseUrl}/api/contact-messages/${m.id}/mark-read`, {}).subscribe({
      next: (updated) => {
        this.messages.update(list => list.map(x => x.id === updated.id ? updated : x));
        this.selected.set(updated);
      }
    });
  }

  formatDate(iso: string): string { return new Date(iso).toLocaleString(); }

  exportCsv(): void {
    this.exporting.set(true);
    let url = `${environment.apiBaseUrl}/api/contact-messages/export.csv`;
    const params: string[] = [];
    if (this.exportFrom) params.push(`from=${this.exportFrom}`);
    if (this.exportTo) params.push(`to=${this.exportTo}`);
    if (params.length) url += '?' + params.join('&');

    this.http.get(url, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = 'contact-messages.csv';
        a.click();
        URL.revokeObjectURL(a.href);
        this.exporting.set(false);
      },
      error: () => this.exporting.set(false)
    });
  }
}
