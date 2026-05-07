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
}

interface BackupTherapist {
  id: string;
  staffNumber: string | null;
  nickname: string;
  gender: string;
  backup: boolean;
  active: boolean;
}

@Component({
  selector: 'sumi-decking',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './decking.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DeckingComponent implements OnInit {
  private http = inject(HttpClient);
  lineup = signal<LineupTherapist[]>([]);
  allTherapists = signal<BackupTherapist[]>([]);
  backupTherapistId: string | null = null;
  backupPosition = 0;

  backupTherapists = computed(() => this.allTherapists().filter(t => t.backup && t.active));

  ngOnInit(): void {
    this.reload();
    this.http.get<BackupTherapist[]>(`${environment.apiBaseUrl}/api/therapists`).subscribe({
      next: (t) => this.allTherapists.set(t)
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
    if (t.flag === 'REQUESTED') return 'Requested';
    if (t.flag === 'BACKUP') return 'Backup';
    if (t.flag === 'SCRUB') return 'Scrub';
    return 'Available';
  }

  statusClass(t: LineupTherapist): string {
    if (t.skipped) return 'bg-amber-100 text-amber-700';
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

  insertBackup(): void {
    if (!this.backupTherapistId) return;
    this.http.post(`${environment.apiBaseUrl}/api/decking/backup/${this.backupTherapistId}?position=${this.backupPosition}`, {}).subscribe({
      next: () => {
        this.backupTherapistId = null;
        this.reload();
      }
    });
  }
}
