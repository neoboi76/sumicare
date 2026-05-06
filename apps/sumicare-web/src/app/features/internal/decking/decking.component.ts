import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

interface DeckingEntry {
  therapistId: string;
  position: number;
  flag: string;
  skipped: boolean;
}

interface TherapistItem {
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
  lineup = signal<DeckingEntry[]>([]);
  therapists = signal<TherapistItem[]>([]);
  backupTherapistId: string | null = null;
  backupPosition = 0;

  backupTherapists = computed(() => this.therapists().filter(t => t.backup && t.active));

  ngOnInit(): void {
    this.reload();
    this.http.get<TherapistItem[]>(`${environment.apiBaseUrl}/api/therapists`).subscribe({
      next: (t) => this.therapists.set(t)
    });
  }

  reload(): void {
    this.http.get<DeckingEntry[]>(`${environment.apiBaseUrl}/api/decking`).subscribe({
      next: (entries) => this.lineup.set(entries),
      error: () => this.lineup.set([])
    });
  }

  nicknameFor(id: string): string {
    return this.therapists().find(t => t.id === id)?.nickname ?? id.substring(0, 8);
  }

  glyph(flag: string): string {
    if (flag === 'REQUESTED') return '♥';
    if (flag === 'SCRUB') return '★';
    if (flag === 'ORDINARY') return '–';
    if (flag === 'BACKUP') return '◯';
    return ' ';
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
