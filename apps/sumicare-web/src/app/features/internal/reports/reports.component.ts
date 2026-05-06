import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { JsonPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

interface DayReport {
  id: string;
  reportDate: string;
  generatedAt: string;
  payload: string;
}

interface MonthlyReport {
  id: string;
  reportYear: number;
  reportMonth: number;
  generatedAt: string;
  payload: string;
}

type Tab = 'cutoff' | 'day' | 'monthly';

@Component({
  selector: 'sumi-reports',
  standalone: true,
  imports: [FormsModule, JsonPipe],
  templateUrl: './reports.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ReportsComponent implements OnInit {
  private http = inject(HttpClient);

  tab = signal<Tab>('cutoff');
  tabs: { value: Tab; label: string }[] = [
    { value: 'cutoff', label: 'Cutoff' },
    { value: 'day', label: 'Day reports' },
    { value: 'monthly', label: 'Monthly reports' }
  ];

  cutoffFrom = '';
  cutoffTo = '';
  cutoffResult = signal<unknown>(null);

  dayFrom = '';
  dayTo = '';
  regenDate = new Date().toISOString().slice(0, 10);
  dayReports = signal<DayReport[]>([]);

  regenYear = new Date().getFullYear();
  regenMonth = new Date().getMonth() + 1;
  monthlyReports = signal<MonthlyReport[]>([]);

  ngOnInit(): void {
    this.loadMonthly();
  }

  runCutoff(event: Event): void {
    event.preventDefault();
    const params = `?from=${encodeURIComponent(this.cutoffFrom)}&to=${encodeURIComponent(this.cutoffTo)}`;
    this.http.get(`${environment.apiBaseUrl}/api/reports/cutoff${params}`).subscribe({
      next: (r) => this.cutoffResult.set(r),
      error: (e) => this.cutoffResult.set({ error: e.message })
    });
  }

  downloadCutoff(): void {
    const params = `?from=${encodeURIComponent(this.cutoffFrom)}&to=${encodeURIComponent(this.cutoffTo)}`;
    window.open(`${environment.apiBaseUrl}/api/reports/cutoff/export${params}`, '_blank');
  }

  loadDays(event: Event): void {
    event.preventDefault();
    const params = `?from=${this.dayFrom}&to=${this.dayTo}`;
    this.http.get<DayReport[]>(`${environment.apiBaseUrl}/api/reports/day${params}`).subscribe({
      next: (d) => this.dayReports.set(d)
    });
  }

  regenerateDay(): void {
    if (!this.regenDate) return;
    this.http.post(`${environment.apiBaseUrl}/api/reports/day/regenerate?date=${this.regenDate}`, {}).subscribe();
  }

  loadMonthly(): void {
    this.http.get<MonthlyReport[]>(`${environment.apiBaseUrl}/api/reports/monthly`).subscribe({
      next: (m) => this.monthlyReports.set(m)
    });
  }

  regenerateMonth(event: Event): void {
    event.preventDefault();
    const params = `?year=${this.regenYear}&month=${this.regenMonth}`;
    this.http.post(`${environment.apiBaseUrl}/api/reports/monthly/regenerate${params}`, {}).subscribe({
      next: () => this.loadMonthly()
    });
  }

  parsePayload(payload: string): unknown {
    try { return JSON.parse(payload); } catch { return payload; }
  }

  formatDate(iso: string): string {
    return new Date(iso).toLocaleString();
  }

  pad(n: number): string {
    return String(n).padStart(2, '0');
  }
}
