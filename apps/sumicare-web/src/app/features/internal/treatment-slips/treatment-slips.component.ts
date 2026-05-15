import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

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
  imports: [FormsModule, RouterLink],
  templateUrl: './treatment-slips.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class TreatmentSlipsComponent implements OnInit {
  private http = inject(HttpClient);
  selectedDate = signal(new Date().toISOString().slice(0, 10));
  slips = signal<TreatmentSlip[]>([]);

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
