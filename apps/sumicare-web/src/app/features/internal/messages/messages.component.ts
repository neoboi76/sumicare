import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
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
  imports: [],
  templateUrl: './messages.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MessagesComponent implements OnInit {
  private http = inject(HttpClient);

  messages = signal<ContactMessage[]>([]);
  filter = signal<'all' | 'unread'>('unread');
  selected = signal<ContactMessage | null>(null);
  loading = signal(false);

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
}
