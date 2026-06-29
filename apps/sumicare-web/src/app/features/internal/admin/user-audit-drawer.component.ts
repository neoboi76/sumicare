/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  OnChanges,
  Output,
  inject,
  signal
} from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

interface AuditEntry {
  id: number;
  actorRole: string;
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
  selector: 'sumi-user-audit-drawer',
  standalone: true,
  templateUrl: './user-audit-drawer.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class UserAuditDrawerComponent implements OnChanges {
  @Input() userId = '';
  @Input() username = '';
  @Output() close = new EventEmitter<void>();

  private http = inject(HttpClient);
  entries = signal<AuditEntry[]>([]);
  page = signal(0);
  totalPages = signal(0);

  ngOnChanges(): void {
    if (this.userId) this.load(0);
  }

  load(page: number): void {
    this.http.get<AuditPage>(
      `${environment.apiBaseUrl}/api/audit-logs?actorUserId=${this.userId}&page=${page}&size=25`
    ).subscribe({
      next: (p) => {
        this.entries.set(p.content ?? []);
        this.page.set(p.number ?? 0);
        this.totalPages.set(p.totalPages ?? 0);
      },
      error: () => this.entries.set([])
    });
  }

  prevPage(): void { if (this.page() > 0) this.load(this.page() - 1); }
  nextPage(): void { if (this.page() < this.totalPages() - 1) this.load(this.page() + 1); }
  fmt(iso: string): string { return new Date(iso).toLocaleString(); }
}
