/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { SlicePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { SortableColumnDirective } from '../../../shared/directives/sortable-column.directive';
import { SortIconComponent } from '../../../shared/components/sort-icon/sort-icon.component';
import { SortState, sortRows } from '../../../shared/utils/compare-by';

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
  imports: [SlicePipe, FormsModule, SortableColumnDirective, SortIconComponent],
  templateUrl: './audit.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AuditComponent implements OnInit {
  private http = inject(HttpClient);
  entries = signal<AuditEntry[]>([]);
  page = signal(0);
  totalPages = signal(0);
  totalElements = signal(0);
  pageSize = signal(50);

  fromDate = '';
  toDate = '';

  setPageSize(size: number): void {
    this.pageSize.set(size);
    this.load(0);
  }

  sortState = signal<SortState>({ key: 'occurredAt', direction: 'desc' });

  sortedEntries = computed(() => sortRows(this.entries(), this.sortState(), (e) => {
    switch (this.sortState().key) {
      case 'occurredAt': return e.occurredAt;
      case 'actorRole': return e.actorRole;
      case 'actionType': return e.actionType;
      case 'targetEntity': return e.targetEntity ?? '';
      case 'ipAddress': return e.ipAddress;
      default: return '';
    }
  }));

  ngOnInit(): void {
    this.load(0);
  }

  load(page: number): void {
    let url = `${environment.apiBaseUrl}/api/audit-logs?page=${page}&size=${this.pageSize()}`;
    if (this.fromDate) url += `&from=${encodeURIComponent(this.fromDate)}`;
    if (this.toDate) url += `&to=${encodeURIComponent(this.toDate)}`;
    this.http.get<AuditPage>(url).subscribe({
      next: (p) => {
        this.entries.set(p.content ?? []);
        this.page.set(p.number ?? 0);
        this.totalPages.set(p.totalPages ?? 0);
        this.totalElements.set(p.totalElements ?? 0);
      },
      error: () => this.entries.set([])
    });
  }

  applyFilter(): void {
    this.load(0);
  }

  clearFilter(): void {
    this.fromDate = '';
    this.toDate = '';
    this.load(0);
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
