import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

interface AuditEntry {
  id: number;
  actorRole: string;
  actionType: string;
  occurredAt: string;
  ipAddress: string;
}

@Component({
  selector: 'sumi-audit',
  standalone: true,
  templateUrl: './audit.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AuditComponent implements OnInit {
  private http = inject(HttpClient);
  entries = signal<AuditEntry[]>([]);

  ngOnInit(): void {
    this.http.get<{ content: AuditEntry[] }>(`${environment.apiBaseUrl}/api/audit-logs`).subscribe({
      next: (page) => this.entries.set(page.content ?? []),
      error: () => this.entries.set([])
    });
  }

  formatDate(iso: string): string {
    return new Date(iso).toLocaleString();
  }
}
