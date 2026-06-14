import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

interface LineupTherapist {
  therapistId: string;
  nickname: string;
  gender: string;
  shiftLabel: string | null;
  flag: string;
  skipped: boolean;
  position: number;
  onCall: boolean;
}

interface Therapist {
  id: string;
  nickname: string;
  gender: string;
  backup: boolean;
  active: boolean;
}

interface Shift {
  id: number;
  label: string;
  startTime: string;
  endTime: string;
}

interface LineupGroup {
  shiftLabel: string;
  therapists: LineupTherapist[];
}

@Component({
  selector: 'sumi-lineup',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './lineup.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LineupComponent implements OnInit {
  private http = inject(HttpClient);
  lineup = signal<LineupTherapist[]>([]);
  allTherapists = signal<Therapist[]>([]);
  allShifts = signal<Shift[]>([]);
  showAddModal = signal(false);
  addTherapistId: string | null = null;
  addShiftId: number | null = null;

  activeTherapists = computed(() => this.allTherapists().filter(t => t.active));

  activeLineup = computed(() => this.lineup().filter(t => !t.skipped));
  onBreakLineup = computed(() => this.lineup().filter(t => t.skipped));

  groupedLineup = computed(() => {
    const list = this.activeLineup();
    const groups: { [key: string]: LineupGroup } = {};
    const result: LineupGroup[] = [];

    for (const t of list) {
      const label = t.shiftLabel || 'Unassigned / Extra';
      if (!groups[label]) {
        groups[label] = { shiftLabel: label, therapists: [] };
        result.push(groups[label]);
      }
      groups[label].therapists.push(t);
    }
    return result;
  });

  ngOnInit(): void {
    this.reload();
    this.http.get<Therapist[]>(`${environment.apiBaseUrl}/api/therapists`).subscribe({
      next: (t) => this.allTherapists.set(t)
    });
    this.http.get<Shift[]>(`${environment.apiBaseUrl}/api/shifts`).subscribe({
      next: (s) => this.allShifts.set(s)
    });
  }

  reload(): void {
    this.http.get<LineupTherapist[]>(`${environment.apiBaseUrl}/api/decking/lineup`).subscribe({
      next: (entries) => this.lineup.set(entries),
      error: () => this.lineup.set([])
    });
  }

  avatarClass(gender: string): string {
    return gender === 'M'
      ? 'bg-blue-100 text-blue-700'
      : 'bg-pink-100 text-pink-700';
  }

  statusLabel(t: LineupTherapist): string {
    if (t.skipped) return 'On break';
    if (t.onCall) return 'On Call';
    if (t.flag === 'REQUESTED') return 'Requested';
    if (t.flag === 'BACKUP') return 'Backup';
    if (t.flag === 'SCRUB') return 'Scrub';
    return 'Available';
  }

  statusClass(t: LineupTherapist): string {
    if (t.skipped) return 'bg-amber-100 text-amber-700';
    if (t.onCall) return 'bg-rose-100 text-rose-700';
    if (t.flag === 'REQUESTED') return 'bg-indigo-100 text-indigo-700';
    if (t.flag === 'BACKUP') return 'bg-slate-100 text-slate-600';
    return 'bg-emerald-100 text-emerald-700';
  }

  setFlag(therapistId: string, flag: string): void {
    this.http.post(`${environment.apiBaseUrl}/api/decking/${therapistId}/flag?flag=${flag}`, {}).subscribe({
      next: () => this.reload()
    });
  }

  skip(therapistId: string): void {
    this.http.post(`${environment.apiBaseUrl}/api/decking/${therapistId}/skip?minutes=30`, {}).subscribe({
      next: () => this.reload()
    });
  }

  cancelSkip(therapistId: string): void {
    this.http.post(`${environment.apiBaseUrl}/api/decking/${therapistId}/skip/cancel`, {}).subscribe({
      next: () => this.reload()
    });
  }

  rotate(therapistId: string): void {
    this.http.post(`${environment.apiBaseUrl}/api/decking/${therapistId}/rotate`, {}).subscribe({
      next: () => this.reload()
    });
  }

  remove(therapistId: string): void {
    this.http.delete(`${environment.apiBaseUrl}/api/decking/${therapistId}`).subscribe({
      next: () => this.reload()
    });
  }

  openAddModal(): void {
    this.addTherapistId = null;
    this.addShiftId = null;
    this.showAddModal.set(true);
  }

  closeAddModal(): void {
    this.showAddModal.set(false);
  }

  confirmAdd(): void {
    if (!this.addTherapistId) return;
    const shiftParam = this.addShiftId != null ? `?shiftId=${this.addShiftId}` : '';
    this.http.post(`${environment.apiBaseUrl}/api/decking/${this.addTherapistId}${shiftParam}`, {}).subscribe({
      next: () => {
        this.showAddModal.set(false);
        this.reload();
      }
    });
  }
}
