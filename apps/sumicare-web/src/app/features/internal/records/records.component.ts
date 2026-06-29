/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { PaginatorComponent } from '../../../shared/components/paginator/paginator.component';
import { ToastService } from '../../../shared/components/toast/toast.service';

interface ServiceLine { serviceId: number | null; serviceName: string; qty: number; unitPrice: number; lineTotal: number; }
interface CutoffServicesReport { from: string; to: string; lines: ServiceLine[]; grandTotal: number; }
interface DailyRow {
  checkInTime: string; orNumber: string; lockerNumber: string; treatment: string;
  packageName: string | null; amount: number; tsn: string; therapist: string; room: string;
  massageStart: string; massageEnd: string; status: string;
  orderId: string | null; firstOfOrder: boolean;
}
interface DailyReport { date: string; rows: DailyRow[]; grandTotal: number; }
interface MonthlyReport { year: number; month: number; rows: DailyRow[]; grandTotal: number; }

interface Therapist { id: string; nickname: string; }
interface Shift { id: number; label: string; startTime: string; endTime: string; }

interface CommissionTherapistRow { therapistId: string; nickname: string; total: number; tip: number; }
interface CommissionShiftReport { shiftId: number; shiftLabel: string; date: string; rows: CommissionTherapistRow[]; grandTotal: number; grandTip: number; }
interface CommissionDailyReport { date: string; rows: CommissionTherapistRow[]; grandTotal: number; grandTip: number; }
interface TipRow { therapistId: string; nickname: string; total: number; count: number; }
interface TipReport { from: string; to: string; rows: TipRow[]; grandTotal: number; }
interface MatrixRow { therapistId: string; nickname: string; amounts: number[]; total: number; }
interface MatrixReport { columnLabels: string[]; rows: MatrixRow[]; columnTotals: number[]; grandTotal: number; }

interface DeckingGlyph { symbol: string; serviceType: string; }
interface DeckingRow { therapistId: string; nickname: string; shiftId: number | null; shiftLabel: string | null; glyphs: DeckingGlyph[]; totalCommission: number; requestedCount: number; }
interface DeckingShiftGroup { shiftId: number; shiftLabel: string; rows: DeckingRow[]; }
interface DeckingDailyReport { date: string; shiftGroups: DeckingShiftGroup[]; }

type Tab = 'services' | 'daily' | 'monthly' | 'commissions' | 'decking' | 'tips';
type CommissionTab = 'shift' | 'daily' | 'cutoff' | 'monthly';

@Component({
  selector: 'sumi-records',
  standalone: true,
  imports: [FormsModule, DecimalPipe, PaginatorComponent],
  templateUrl: './records.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RecordsComponent implements OnInit {
  private http = inject(HttpClient);
  private toast = inject(ToastService);

  tab = signal<Tab>('services');
  commissionTab = signal<CommissionTab>('cutoff');
  loading = signal(false);

  cutoffFrom = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Manila' }).format(new Date());
  cutoffTo = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Manila' }).format(new Date());
  cutoffShiftId: number | null = null;
  servicesReport = signal<CutoffServicesReport | null>(null);

  selectedShiftLabel = (): string => {
    if (!this.cutoffShiftId) return 'All shifts';
    const s = this.shifts().find(sh => sh.id === this.cutoffShiftId);
    return s ? `${s.label} (${s.startTime} - ${s.endTime})` : 'All shifts';
  };

  dailyDate = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Manila' }).format(new Date());
  dailyReport = signal<DailyReport | null>(null);

  monthlyYear = new Date().getFullYear();
  monthlyMonth = new Date().getMonth() + 1;
  monthlyReport = signal<MonthlyReport | null>(null);

  shifts = signal<Shift[]>([]);
  commissionShiftId: number | null = null;
  commissionShiftDate = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Manila' }).format(new Date());
  commissionShiftReport = signal<CommissionShiftReport | null>(null);

  commissionDailyDate = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Manila' }).format(new Date());
  commissionDailyReport = signal<CommissionDailyReport | null>(null);

  commissionCutoffYear = new Date().getFullYear();
  commissionCutoffMonth = new Date().getMonth() + 1;
  commissionCutoffHalf: 1 | 2 = new Date().getDate() <= 15 ? 1 : 2;
  commissionCutoffReport = signal<MatrixReport | null>(null);

  commissionMonthlyYear = new Date().getFullYear();
  commissionMonthlyMonth = new Date().getMonth() + 1;
  commissionMonthlyReport = signal<MatrixReport | null>(null);

  deckingDate = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Manila' }).format(new Date());
  deckingReport = signal<DeckingDailyReport | null>(null);

  tipsFrom = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Manila' }).format(new Date());
  tipsTo = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Manila' }).format(new Date());
  tipsTherapistId = '';
  therapists = signal<Therapist[]>([]);
  tipsReport = signal<TipReport | null>(null);

  pageSize = signal(15);
  dailyPage = signal(0);

  pagedDailyRows = computed(() => {
    const rows = this.dailyReport()?.rows ?? [];
    const start = this.dailyPage() * this.pageSize();
    return rows.slice(start, start + this.pageSize());
  });

  ngOnInit(): void {
    this.loadShifts();
    this.loadTherapists();
  }

  private loadTherapists(): void {
    this.http.get<Therapist[]>(`${environment.apiBaseUrl}/api/therapists`).subscribe({
      next: (t) => this.therapists.set(t),
      error: () => this.therapists.set([])
    });
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
    const from = `${this.cutoffFrom}T00:00:00.000+08:00`;
    const to = `${this.cutoffTo}T23:59:59.999+08:00`;
    let url = `${environment.apiBaseUrl}/api/records/cutoff/services?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`;
    if (this.cutoffShiftId) url += `&shiftId=${this.cutoffShiftId}`;
    this.http.get<CutoffServicesReport>(url).subscribe({
      next: (r) => { this.servicesReport.set(r); this.loading.set(false); },
      error: () => { this.servicesReport.set(null); this.loading.set(false); }
    });
  }

  exportServicesXlsx(): void {
    const from = `${this.cutoffFrom}T00:00:00.000+08:00`;
    const to = `${this.cutoffTo}T23:59:59.999+08:00`;
    let url = `${environment.apiBaseUrl}/api/records/cutoff/services/export.xlsx?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`;
    if (this.cutoffShiftId) url += `&shiftId=${this.cutoffShiftId}`;
    this.downloadBlob(url, `cutoff-services-${this.cutoffFrom}-to-${this.cutoffTo}.xlsx`);
  }

  loadDaily(): void {
    this.loading.set(true);
    this.http.get<DailyReport>(`${environment.apiBaseUrl}/api/records/daily?date=${this.dailyDate}`).subscribe({
      next: (r) => { this.dailyReport.set(r); this.dailyPage.set(0); this.loading.set(false); },
      error: () => { this.dailyReport.set(null); this.loading.set(false); }
    });
  }
  exportDailyXlsx(): void {
    this.downloadBlob(
      `${environment.apiBaseUrl}/api/records/daily/export.xlsx?date=${this.dailyDate}`,
      `daily-${this.dailyDate}.xlsx`
    );
  }

  loadMonthly(): void {
    this.loading.set(true);
    this.http.get<MonthlyReport>(
      `${environment.apiBaseUrl}/api/records/monthly-detailed?year=${this.monthlyYear}&month=${this.monthlyMonth}`
    ).subscribe({
      next: (r) => { this.monthlyReport.set(r); this.loading.set(false); },
      error: () => { this.monthlyReport.set(null); this.loading.set(false); }
    });
  }
  exportMonthlyXlsx(): void {
    this.downloadBlob(
      `${environment.apiBaseUrl}/api/records/monthly-detailed/export.xlsx?year=${this.monthlyYear}&month=${this.monthlyMonth}`,
      `monthly-${this.monthlyYear}-${String(this.monthlyMonth).padStart(2, '0')}.xlsx`
    );
  }

  loadCommissionShift(): void {
    if (!this.commissionShiftId) return;
    this.loading.set(true);
    this.http.get<CommissionShiftReport>(
      `${environment.apiBaseUrl}/api/records/commissions/shift?shiftId=${this.commissionShiftId}&date=${this.commissionShiftDate}`
    ).subscribe({
      next: (r) => { this.commissionShiftReport.set(r); this.loading.set(false); },
      error: () => { this.commissionShiftReport.set(null); this.loading.set(false); }
    });
  }
  exportCommissionShiftXlsx(): void {
    if (!this.commissionShiftId) return;
    this.downloadBlob(
      `${environment.apiBaseUrl}/api/records/commissions/shift/export.xlsx?shiftId=${this.commissionShiftId}&date=${this.commissionShiftDate}`,
      `commissions-shift-${this.commissionShiftId}-${this.commissionShiftDate}.xlsx`
    );
  }

  loadCommissionDaily(): void {
    this.loading.set(true);
    this.http.get<CommissionDailyReport>(
      `${environment.apiBaseUrl}/api/records/commissions/daily?date=${this.commissionDailyDate}`
    ).subscribe({
      next: (r) => { this.commissionDailyReport.set(r); this.loading.set(false); },
      error: () => { this.commissionDailyReport.set(null); this.loading.set(false); }
    });
  }
  exportCommissionDailyXlsx(): void {
    this.downloadBlob(
      `${environment.apiBaseUrl}/api/records/commissions/daily/export.xlsx?date=${this.commissionDailyDate}`,
      `commissions-daily-${this.commissionDailyDate}.xlsx`
    );
  }

  loadCommissionCutoff(): void {
    this.loading.set(true);
    this.http.get<MatrixReport>(
      `${environment.apiBaseUrl}/api/records/commissions/cutoff?year=${this.commissionCutoffYear}&month=${this.commissionCutoffMonth}&half=${this.commissionCutoffHalf}`
    ).subscribe({
      next: (r) => { this.commissionCutoffReport.set(r); this.loading.set(false); },
      error: () => { this.commissionCutoffReport.set(null); this.loading.set(false); }
    });
  }
  exportCommissionCutoffXlsx(): void {
    this.downloadBlob(
      `${environment.apiBaseUrl}/api/records/commissions/cutoff/export.xlsx?year=${this.commissionCutoffYear}&month=${this.commissionCutoffMonth}&half=${this.commissionCutoffHalf}`,
      `commissions-cutoff-${this.commissionCutoffYear}-${String(this.commissionCutoffMonth).padStart(2, '0')}-h${this.commissionCutoffHalf}.xlsx`
    );
  }

  loadCommissionMonthly(): void {
    this.loading.set(true);
    this.http.get<MatrixReport>(
      `${environment.apiBaseUrl}/api/records/commissions/monthly?year=${this.commissionMonthlyYear}&month=${this.commissionMonthlyMonth}`
    ).subscribe({
      next: (r) => { this.commissionMonthlyReport.set(r); this.loading.set(false); },
      error: () => { this.commissionMonthlyReport.set(null); this.loading.set(false); }
    });
  }
  exportCommissionMonthlyXlsx(): void {
    this.downloadBlob(
      `${environment.apiBaseUrl}/api/records/commissions/monthly/export.xlsx?year=${this.commissionMonthlyYear}&month=${this.commissionMonthlyMonth}`,
      `commissions-monthly-${this.commissionMonthlyYear}-${String(this.commissionMonthlyMonth).padStart(2, '0')}.xlsx`
    );
  }

  loadDecking(): void {
    this.loading.set(true);
    this.http.get<DeckingDailyReport>(
      `${environment.apiBaseUrl}/api/records/decking/daily?date=${this.deckingDate}`
    ).subscribe({
      next: (r) => { this.deckingReport.set(r); this.loading.set(false); },
      error: () => { this.deckingReport.set(null); this.loading.set(false); }
    });
  }
  exportDeckingXlsx(): void {
    this.downloadBlob(
      `${environment.apiBaseUrl}/api/records/decking/daily/export.xlsx?date=${this.deckingDate}`,
      `decking-${this.deckingDate}.xlsx`
    );
  }

  private tipsQuery(): string {
    let q = `from=${this.tipsFrom}&to=${this.tipsTo}`;
    if (this.tipsTherapistId) q += `&therapistId=${this.tipsTherapistId}`;
    return q;
  }

  loadTips(): void {
    this.loading.set(true);
    this.http.get<TipReport>(
      `${environment.apiBaseUrl}/api/records/commissions/tips?${this.tipsQuery()}`
    ).subscribe({
      next: (r) => { this.tipsReport.set(r); this.loading.set(false); },
      error: () => { this.tipsReport.set(null); this.loading.set(false); }
    });
  }
  exportTipsXlsx(): void {
    this.downloadBlob(
      `${environment.apiBaseUrl}/api/records/commissions/tips/export.xlsx?${this.tipsQuery()}`,
      `tips-${this.tipsFrom}-to-${this.tipsTo}.xlsx`
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
      error: () => this.toast.error('Export failed.')
    });
  }
}
