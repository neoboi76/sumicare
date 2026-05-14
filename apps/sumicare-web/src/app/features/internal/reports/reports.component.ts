import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

interface ServiceLine { serviceId: number | null; serviceName: string; qty: number; unitPrice: number; lineTotal: number; }
interface CutoffServicesReport { from: string; to: string; lines: ServiceLine[]; grandTotal: number; }
interface DailyRow {
  checkInTime: string; orNumber: string; lockerNumber: string; treatment: string;
  amount: number; tsn: string; therapist: string; room: string;
  massageStart: string; massageEnd: string; status: string;
}
interface DailyReport { date: string; rows: DailyRow[]; grandTotal: number; }
interface MonthlyReport { year: number; month: number; rows: DailyRow[]; grandTotal: number; }

interface Therapist { id: string; nickname: string; }
interface Shift { id: number; label: string; startTime: string; endTime: string; }

interface CommissionTherapistRow { therapistId: string; nickname: string; total: number; }
interface CommissionShiftReport { shiftId: number; shiftLabel: string; date: string; rows: CommissionTherapistRow[]; grandTotal: number; }
interface CommissionDailyReport { date: string; rows: CommissionTherapistRow[]; grandTotal: number; }
interface MatrixRow { therapistId: string; nickname: string; amounts: number[]; total: number; }
interface MatrixReport { columnLabels: string[]; rows: MatrixRow[]; columnTotals: number[]; grandTotal: number; }

interface DeckingGlyph { symbol: string; serviceType: string; }
interface DeckingRow { therapistId: string; nickname: string; shiftId: number | null; shiftLabel: string | null; glyphs: DeckingGlyph[]; totalCommission: number; requestedCount: number; }
interface DeckingShiftGroup { shiftId: number; shiftLabel: string; rows: DeckingRow[]; }
interface DeckingDailyReport { date: string; shiftGroups: DeckingShiftGroup[]; }

type Tab = 'services' | 'daily' | 'monthly' | 'commissions' | 'decking';
type CommissionTab = 'shift' | 'daily' | 'cutoff' | 'monthly';

@Component({
  selector: 'sumi-reports',
  standalone: true,
  imports: [FormsModule, DecimalPipe],
  templateUrl: './reports.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ReportsComponent implements OnInit {
  private http = inject(HttpClient);

  tab = signal<Tab>('services');
  commissionTab = signal<CommissionTab>('cutoff');
  loading = signal(false);

  cutoffFrom = new Date().toISOString().slice(0, 10);
  cutoffTo = new Date().toISOString().slice(0, 10);
  cutoffShiftId: number | null = null;
  servicesReport = signal<CutoffServicesReport | null>(null);

  selectedShiftLabel = (): string => {
    if (!this.cutoffShiftId) return 'All shifts';
    const s = this.shifts().find(sh => sh.id === this.cutoffShiftId);
    return s ? `${s.label} (${s.startTime} - ${s.endTime})` : 'All shifts';
  };

  dailyDate = new Date().toISOString().slice(0, 10);
  dailyReport = signal<DailyReport | null>(null);

  monthlyYear = new Date().getFullYear();
  monthlyMonth = new Date().getMonth() + 1;
  monthlyReport = signal<MonthlyReport | null>(null);

  shifts = signal<Shift[]>([]);
  commissionShiftId: number | null = null;
  commissionShiftDate = new Date().toISOString().slice(0, 10);
  commissionShiftReport = signal<CommissionShiftReport | null>(null);

  commissionDailyDate = new Date().toISOString().slice(0, 10);
  commissionDailyReport = signal<CommissionDailyReport | null>(null);

  commissionCutoffYear = new Date().getFullYear();
  commissionCutoffMonth = new Date().getMonth() + 1;
  commissionCutoffHalf: 1 | 2 = new Date().getDate() <= 15 ? 1 : 2;
  commissionCutoffReport = signal<MatrixReport | null>(null);

  commissionMonthlyYear = new Date().getFullYear();
  commissionMonthlyMonth = new Date().getMonth() + 1;
  commissionMonthlyReport = signal<MatrixReport | null>(null);

  deckingDate = new Date().toISOString().slice(0, 10);
  deckingReport = signal<DeckingDailyReport | null>(null);

  ngOnInit(): void {
    this.loadShifts();
  }

  setTab(t: Tab): void { this.tab.set(t); }
  setCommissionTab(t: CommissionTab): void { this.commissionTab.set(t); }

  private loadShifts(): void {
    this.http.get<Shift[]>(`${environment.apiBaseUrl}/api/shifts`).subscribe({
      next: (s) => this.shifts.set(s),
      error: () => this.shifts.set([])
    });
  }

  loadServices(): void {
    this.loading.set(true);
    const from = `${this.cutoffFrom}T00:00:00Z`;
    const to = `${this.cutoffTo}T23:59:59Z`;
    let url = `${environment.apiBaseUrl}/api/reports/cutoff/services?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`;
    if (this.cutoffShiftId) url += `&shiftId=${this.cutoffShiftId}`;
    this.http.get<CutoffServicesReport>(url).subscribe({
      next: (r) => { this.servicesReport.set(r); this.loading.set(false); },
      error: () => { this.servicesReport.set(null); this.loading.set(false); }
    });
  }

  exportServicesCsv(): void {
    const from = `${this.cutoffFrom}T00:00:00Z`;
    const to = `${this.cutoffTo}T23:59:59Z`;
    let url = `${environment.apiBaseUrl}/api/reports/cutoff/services/export.csv?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`;
    if (this.cutoffShiftId) url += `&shiftId=${this.cutoffShiftId}`;
    this.downloadBlob(url, `cutoff-services-${this.cutoffFrom}-to-${this.cutoffTo}.csv`);
  }

  loadDaily(): void {
    this.loading.set(true);
    this.http.get<DailyReport>(`${environment.apiBaseUrl}/api/reports/daily?date=${this.dailyDate}`).subscribe({
      next: (r) => { this.dailyReport.set(r); this.loading.set(false); },
      error: () => { this.dailyReport.set(null); this.loading.set(false); }
    });
  }
  exportDailyCsv(): void {
    this.downloadBlob(
      `${environment.apiBaseUrl}/api/reports/daily/export.csv?date=${this.dailyDate}`,
      `daily-${this.dailyDate}.csv`
    );
  }

  loadMonthly(): void {
    this.loading.set(true);
    this.http.get<MonthlyReport>(
      `${environment.apiBaseUrl}/api/reports/monthly-detailed?year=${this.monthlyYear}&month=${this.monthlyMonth}`
    ).subscribe({
      next: (r) => { this.monthlyReport.set(r); this.loading.set(false); },
      error: () => { this.monthlyReport.set(null); this.loading.set(false); }
    });
  }
  exportMonthlyCsv(): void {
    this.downloadBlob(
      `${environment.apiBaseUrl}/api/reports/monthly-detailed/export.csv?year=${this.monthlyYear}&month=${this.monthlyMonth}`,
      `monthly-${this.monthlyYear}-${String(this.monthlyMonth).padStart(2, '0')}.csv`
    );
  }

  loadCommissionShift(): void {
    if (!this.commissionShiftId) return;
    this.loading.set(true);
    this.http.get<CommissionShiftReport>(
      `${environment.apiBaseUrl}/api/reports/commissions/shift?shiftId=${this.commissionShiftId}&date=${this.commissionShiftDate}`
    ).subscribe({
      next: (r) => { this.commissionShiftReport.set(r); this.loading.set(false); },
      error: () => { this.commissionShiftReport.set(null); this.loading.set(false); }
    });
  }
  exportCommissionShiftCsv(): void {
    if (!this.commissionShiftId) return;
    this.downloadBlob(
      `${environment.apiBaseUrl}/api/reports/commissions/shift/export.csv?shiftId=${this.commissionShiftId}&date=${this.commissionShiftDate}`,
      `commissions-shift-${this.commissionShiftId}-${this.commissionShiftDate}.csv`
    );
  }

  loadCommissionDaily(): void {
    this.loading.set(true);
    this.http.get<CommissionDailyReport>(
      `${environment.apiBaseUrl}/api/reports/commissions/daily?date=${this.commissionDailyDate}`
    ).subscribe({
      next: (r) => { this.commissionDailyReport.set(r); this.loading.set(false); },
      error: () => { this.commissionDailyReport.set(null); this.loading.set(false); }
    });
  }
  exportCommissionDailyCsv(): void {
    this.downloadBlob(
      `${environment.apiBaseUrl}/api/reports/commissions/daily/export.csv?date=${this.commissionDailyDate}`,
      `commissions-daily-${this.commissionDailyDate}.csv`
    );
  }

  loadCommissionCutoff(): void {
    this.loading.set(true);
    this.http.get<MatrixReport>(
      `${environment.apiBaseUrl}/api/reports/commissions/cutoff?year=${this.commissionCutoffYear}&month=${this.commissionCutoffMonth}&half=${this.commissionCutoffHalf}`
    ).subscribe({
      next: (r) => { this.commissionCutoffReport.set(r); this.loading.set(false); },
      error: () => { this.commissionCutoffReport.set(null); this.loading.set(false); }
    });
  }
  exportCommissionCutoffCsv(): void {
    this.downloadBlob(
      `${environment.apiBaseUrl}/api/reports/commissions/cutoff/export.csv?year=${this.commissionCutoffYear}&month=${this.commissionCutoffMonth}&half=${this.commissionCutoffHalf}`,
      `commissions-cutoff-${this.commissionCutoffYear}-${String(this.commissionCutoffMonth).padStart(2, '0')}-h${this.commissionCutoffHalf}.csv`
    );
  }

  loadCommissionMonthly(): void {
    this.loading.set(true);
    this.http.get<MatrixReport>(
      `${environment.apiBaseUrl}/api/reports/commissions/monthly?year=${this.commissionMonthlyYear}&month=${this.commissionMonthlyMonth}`
    ).subscribe({
      next: (r) => { this.commissionMonthlyReport.set(r); this.loading.set(false); },
      error: () => { this.commissionMonthlyReport.set(null); this.loading.set(false); }
    });
  }
  exportCommissionMonthlyCsv(): void {
    this.downloadBlob(
      `${environment.apiBaseUrl}/api/reports/commissions/monthly/export.csv?year=${this.commissionMonthlyYear}&month=${this.commissionMonthlyMonth}`,
      `commissions-monthly-${this.commissionMonthlyYear}-${String(this.commissionMonthlyMonth).padStart(2, '0')}.csv`
    );
  }

  loadDecking(): void {
    this.loading.set(true);
    this.http.get<DeckingDailyReport>(
      `${environment.apiBaseUrl}/api/reports/decking/daily?date=${this.deckingDate}`
    ).subscribe({
      next: (r) => { this.deckingReport.set(r); this.loading.set(false); },
      error: () => { this.deckingReport.set(null); this.loading.set(false); }
    });
  }
  exportDeckingCsv(): void {
    this.downloadBlob(
      `${environment.apiBaseUrl}/api/reports/decking/daily/export.csv?date=${this.deckingDate}`,
      `decking-${this.deckingDate}.csv`
    );
  }

  private downloadBlob(url: string, filename: string): void {
    this.http.get(url, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        const obj = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = obj; a.download = filename;
        document.body.appendChild(a); a.click(); document.body.removeChild(a);
        URL.revokeObjectURL(obj);
      },
      error: () => alert('Export failed.')
    });
  }
}
