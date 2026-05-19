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
  template: `
    <div class="fixed inset-0 bg-black/40 flex items-end sm:items-center justify-center z-50 p-4"
         (click)="close.emit()">
      <div class="bg-white rounded-xl shadow-xl w-full max-w-2xl max-h-[80vh] flex flex-col"
           (click)="$event.stopPropagation()">
        <div class="flex items-center justify-between px-5 py-4 border-b">
          <h3 class="font-semibold text-slate-900">Activity log — {{ username }}</h3>
          <button (click)="close.emit()" class="text-slate-400 hover:text-slate-600 text-lg leading-none">&#10005;</button>
        </div>
        <div class="overflow-y-auto flex-1 text-sm">
          <table class="w-full">
            <thead class="bg-slate-50 sticky top-0">
              <tr>
                <th class="text-left px-4 py-2">When</th>
                <th class="text-left px-4 py-2">Action</th>
                <th class="text-left px-4 py-2">Target</th>
              </tr>
            </thead>
            <tbody>
              @for (entry of entries(); track entry.id) {
                <tr class="border-t hover:bg-slate-50">
                  <td class="px-4 py-2 font-mono text-xs whitespace-nowrap">{{ fmt(entry.occurredAt) }}</td>
                  <td class="px-4 py-2">{{ entry.actionType }}</td>
                  <td class="px-4 py-2 font-mono text-xs">
                    @if (entry.targetEntity) {
                      {{ entry.targetEntity }}@if (entry.targetId) { /{{ entry.targetId.slice(0,8) }} }
                    }
                  </td>
                </tr>
              } @empty {
                <tr><td colspan="3" class="px-4 py-6 text-center text-slate-500">No activity recorded.</td></tr>
              }
            </tbody>
          </table>
        </div>
        @if (totalPages() > 1) {
          <div class="px-5 py-3 border-t flex items-center justify-between text-sm text-slate-600">
            <button (click)="prevPage()" [disabled]="page() === 0"
                    class="px-3 py-1 rounded border border-slate-300 disabled:opacity-40">Prev</button>
            <span>{{ page() + 1 }} / {{ totalPages() }}</span>
            <button (click)="nextPage()" [disabled]="page() >= totalPages() - 1"
                    class="px-3 py-1 rounded border border-slate-300 disabled:opacity-40">Next</button>
          </div>
        }
      </div>
    </div>
  `,
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
