/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { environment } from '../../../../environments/environment';

interface AttendanceRecord {
  id: number;
  therapistId: string;
  eventType: string;
  eventAt: string;
  deviceId: string | null;
  remarks: string | null;
}

@Component({
  selector: 'sumi-attendance',
  templateUrl: './attendance.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule]
})
export class AttendanceComponent implements OnInit {
  private http = inject(HttpClient);

  records = signal<AttendanceRecord[]>([]);
  loading = signal(false);
  fromDate = signal(new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Manila' }).format(new Date()));
  toDate = signal(new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Manila' }).format(new Date()));

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    const from = `${this.fromDate()}T00:00:00Z`;
    const to = `${this.toDate()}T23:59:59Z`;
    this.http.get<AttendanceRecord[]>(
      `${environment.apiBaseUrl}/api/attendance?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`
    ).subscribe({
      next: (r) => { this.records.set(r); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  eventBadgeClass(type: string): string {
    switch (type) {
      case 'CLOCK_IN': return 'bg-green-100 text-green-700';
      case 'CLOCK_OUT': return 'bg-blue-100 text-blue-700';
      case 'ABSENT': return 'bg-red-100 text-red-700';
      case 'DAY_OFF': return 'bg-yellow-100 text-yellow-700';
      default: return 'bg-gray-100 text-gray-600';
    }
  }

  formatTime(iso: string): string {
    return new Date(iso).toLocaleTimeString('en-PH', { hour: '2-digit', minute: '2-digit' });
  }
}
