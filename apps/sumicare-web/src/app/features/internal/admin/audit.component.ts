import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { SlicePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

interface AuditEntry {
  id: number;
  actorRole: string;
  actorUserId: string;
  actionType: string;
  occurredAt: string;
  ipAddress: string;
  targetEntity: string | null;
  targetId: string | null;
}

interface AuditPage {
  content: AuditEntry[];
  totalPages: number;
  number: number;
  totalElements: number;
}

@Component({
  selector: 'sumi-audit',
  standalone: true,
  imports: [SlicePipe],
  templateUrl: './audit.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AuditComponent implements OnInit {
  private http = inject(HttpClient);
  entries = signal<AuditEntry[]>([]);
  page = signal(0);
  totalPages = signal(0);
  totalElements = signal(0);
  readonly pageSize = 50;

  ngOnInit(): void {
    this.load(0);
  }

  load(page: number): void {
    this.http
      .get<AuditPage>(`${environment.apiBaseUrl}/api/audit-logs?page=${page}&size=${this.pageSize}`)
      .subscribe({
        next: (p) => {
          this.entries.set(p.content ?? []);
          this.page.set(p.number ?? 0);
          this.totalPages.set(p.totalPages ?? 0);
          this.totalElements.set(p.totalElements ?? 0);
        },
        error: () => this.entries.set([])
      });
  }

  prevPage(): void {
    if (this.page() > 0) this.load(this.page() - 1);
  }

  nextPage(): void {
    if (this.page() < this.totalPages() - 1) this.load(this.page() + 1);
  }

  formatDate(iso: string): string {
    return new Date(iso).toLocaleString();
  }
}
