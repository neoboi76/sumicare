import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { SortableColumnDirective } from '../../../shared/directives/sortable-column.directive';
import { SortIconComponent } from '../../../shared/components/sort-icon/sort-icon.component';
import { SortState, sortRows } from '../../../shared/utils/compare-by';

interface TreatmentSlip {
  id: string;
  tsn: string;
  clientNickname: string;
  lockerNumber: string | null;
  serviceName: string;
  primaryTherapistNickname: string | null;
  secondaryTherapistNickname: string | null;
  roomNumber: string | null;
  startTime: string | null;
  endTime: string | null;
  orNumber: string | null;
  vip: boolean;
  createdAt: string;
}

@Component({
  selector: 'sumi-treatment-slips',
  standalone: true,
  imports: [FormsModule, RouterLink, SortableColumnDirective, SortIconComponent],
  templateUrl: './treatment-slips.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class TreatmentSlipsComponent implements OnInit {
  private http = inject(HttpClient);
  private router = inject(Router);
  selectedDate = signal(new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Manila' }).format(new Date()));
  slips = signal<TreatmentSlip[]>([]);

  showCreate = signal(false);
  createError = signal<string | null>(null);
  newSlipNickname = '';
  newSlipVip = false;

  sortState = signal<SortState>({ key: 'tsn', direction: 'asc' });

  sortedSlips = computed(() => {
    const state = this.sortState();
    return sortRows(this.slips(), state, (s) => {
      switch (state.key) {
        case 'tsn': return s.tsn;
        case 'clientNickname': return s.clientNickname;
        case 'serviceName': return s.serviceName;
        case 'roomNumber': return s.roomNumber ?? '';
        case 'primaryTherapistNickname': return s.primaryTherapistNickname ?? '';
        case 'startTime': return s.startTime ?? '';
        case 'endTime': return s.endTime ?? '';
        case 'vip': return s.vip ? 1 : 0;
        default: return '';
      }
    });
  });

  ngOnInit(): void {
    this.reload();
  }

  onDateChange(value: string): void {
    this.selectedDate.set(value);
    this.reload();
  }

  reload(): void {
    const { from, to } = this.dayBounds();
    const params = `?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`;
    this.http.get<TreatmentSlip[]>(`${environment.apiBaseUrl}/api/treatment-slips${params}`).subscribe({
      next: (s) => this.slips.set(s),
      error: () => this.slips.set([])
    });
  }

  openCreate(): void {
    this.newSlipNickname = '';
    this.newSlipVip = false;
    this.createError.set(null);
    this.showCreate.set(true);
  }

  closeCreate(): void {
    this.showCreate.set(false);
  }

  createSlip(): void {
    if (!this.newSlipNickname.trim()) {
      this.createError.set('Client nickname is required.');
      return;
    }
    const payload = { clientNickname: this.newSlipNickname.trim(), vip: this.newSlipVip };
    this.http.post<{ id: string }>(`${environment.apiBaseUrl}/api/treatment-slips`, payload).subscribe({
      next: (slip) => { this.showCreate.set(false); this.router.navigate(['/app/treatment-slips', slip.id]); },
      error: (err) => this.createError.set(err?.error?.message || 'Could not create treatment slip.')
    });
  }

  exportCsv(): void {
    const { from, to } = this.dayBounds();
    const params = `?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`;
    this.http.get(`${environment.apiBaseUrl}/api/treatment-slips/export.csv${params}`,
      { responseType: 'blob' }
    ).subscribe(blob => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `treatment-slips-${this.selectedDate()}.csv`;
      a.click();
      URL.revokeObjectURL(url);
    });
  }

  formatRange(start: string | null, end: string | null): string {
    const f = (iso: string | null) => iso ? new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '-';
    return `${f(start)} - ${f(end)}`;
  }

  formatTime(iso: string | null): string {
    if (!iso) return '-';
    return new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }

  private dayBounds(): { from: string; to: string } {
    const d = this.selectedDate();
    return {
      from: `${d}T00:00:00.000Z`,
      to: `${d}T23:59:59.999Z`
    };
  }
}
