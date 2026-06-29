/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, OnDestroy, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import Chart from 'chart.js/auto';
import { environment } from '../../../../environments/environment';

interface RevenuePoint {
  date: string;
  net: number;
  inflow: number;
  outflow: number;
  count: number;
}

interface Balance {
  inflow: number;
  outflow: number;
  balance: number;
  count: number;
}

interface MethodSlice {
  method: string;
  net: number;
}

interface TopTherapist {
  therapistId: string;
  nickname: string;
  averageRating: number;
  ratingCount: number;
  requestCount: number;
  serviceCount: number;
  score: number;
}

interface NameCount { name: string; count: number; }
interface TherapistPerformance {
  therapistId: string;
  nickname: string;
  revenue: number;
  commissions: number;
  tips: number;
  servicesRendered: number;
  specificRequests: number;
  topClients: NameCount[];
  topServices: NameCount[];
}
interface RegisteredClientRow {
  clientId: string;
  nickname: string;
  bookingCount: number;
  totalSpending: number;
  topService: string;
  topPackage: string;
}

interface SatisfactionStats {
  overallDistribution: { counts: Record<number, number>; average: number; total: number };
  perCriterionAverages: Record<string, number>;
  satisfactionIndex: number;
  nps: { score: number; promoters: number; passives: number; detractors: number; respondents: number };
  recentComments: string[];
}

const PAYMENT_METHODS = ['CASH', 'GCASH', 'CREDIT', 'DEBIT'];
const TOP_THERAPIST_PERIODS = ['ALL', 'MONTHLY', 'WEEKLY', 'PAYROLL'] as const;
type TopTherapistPeriod = typeof TOP_THERAPIST_PERIODS[number];

@Component({
  selector: 'sumi-reports',
  standalone: true,
  imports: [FormsModule, DecimalPipe],
  templateUrl: './reports.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ReportsComponent implements AfterViewInit, OnDestroy {
  private http = inject(HttpClient);

  private trendCanvas = viewChild<ElementRef<HTMLCanvasElement>>('trendCanvas');
  private methodCanvas = viewChild<ElementRef<HTMLCanvasElement>>('methodCanvas');
  private trendChart: Chart | null = null;
  private methodChart: Chart | null = null;

  from = signal(this.dayOffset(-13));
  to = signal(this.dayOffset(0));
  method = signal('ALL');
  groupBy = signal<'SERVICE' | 'PACKAGE'>('SERVICE');
  topTherapistPeriod = signal<TopTherapistPeriod>('ALL');
  loading = signal(false);
  points = signal<RevenuePoint[]>([]);
  methodSlices = signal<MethodSlice[]>([]);
  topTherapists = signal<TopTherapist[]>([]);
  performance = signal<TherapistPerformance[]>([]);
  registeredClients = signal<RegisteredClientRow[]>([]);
  satisfaction = signal<SatisfactionStats | null>(null);

  readonly methodOptions = ['ALL', ...PAYMENT_METHODS];
  readonly groupByOptions: { value: 'SERVICE' | 'PACKAGE'; label: string }[] = [
    { value: 'SERVICE', label: 'Service' },
    { value: 'PACKAGE', label: 'Package' }
  ];
  readonly topTherapistPeriods = TOP_THERAPIST_PERIODS;

  narrative = computed(() => {
    const count = this.totalCount();
    if (count === 0) return 'No ledger activity in the selected range yet.';
    const net = this.totalNet();
    const days = Math.max(1, this.points().length);
    const best = this.bestDay();
    const avg = net / days;
    const peak = best ? ` The peak day was ${best.date} at ₱${best.net.toFixed(2)}.` : '';
    return `Net revenue of ₱${net.toFixed(2)} across ${count} transactions, averaging ₱${avg.toFixed(2)} per day over ${days} days.${peak}`;
  });

  totalClientSpend = computed(() => this.registeredClients().reduce((sum, c) => sum + c.totalSpending, 0));

  totalNet = computed(() => this.points().reduce((sum, p) => sum + p.net, 0));
  totalInflow = computed(() => this.points().reduce((sum, p) => sum + p.inflow, 0));
  totalOutflow = computed(() => this.points().reduce((sum, p) => sum + p.outflow, 0));
  totalCount = computed(() => this.points().reduce((sum, p) => sum + p.count, 0));
  bestDay = computed(() => {
    const rows = this.points();
    if (rows.length === 0) return null;
    return rows.reduce((best, p) => (p.net > best.net ? p : best), rows[0]);
  });

  satisfactionCriteria = computed(() => {
    const s = this.satisfaction();
    if (!s || !s.perCriterionAverages) return [];
    return Object.entries(s.perCriterionAverages).map(([key, avg]) => ({
      label: this.humanizeKey(key),
      avg
    }));
  });

  ngAfterViewInit(): void {
    this.load();
    this.loadTopTherapists();
    this.loadRegisteredClients();
    this.loadSatisfaction();
  }

  loadTopTherapists(): void {
    this.http.get<{ therapists: TopTherapist[] }>(
      `${environment.apiBaseUrl}/api/reports/top-therapists?period=${this.topTherapistPeriod()}`
    ).pipe(catchError(() => of({ therapists: [] as TopTherapist[] })))
      .subscribe((res) => this.topTherapists.set(res.therapists));
  }

  loadPerformance(): void {
    this.http.get<{ therapists: TherapistPerformance[] }>(
      `${environment.apiBaseUrl}/api/reports/therapist-performance?from=${this.from()}&to=${this.to()}`
    ).pipe(catchError(() => of({ therapists: [] as TherapistPerformance[] })))
      .subscribe((res) => this.performance.set(res.therapists));
  }

  loadRegisteredClients(): void {
    this.http.get<{ clients: RegisteredClientRow[] }>(`${environment.apiBaseUrl}/api/reports/registered-clients`)
      .pipe(catchError(() => of({ clients: [] as RegisteredClientRow[] })))
      .subscribe((res) => this.registeredClients.set(res.clients));
  }

  loadSatisfaction(): void {
    this.http.get<SatisfactionStats>(
      `${environment.apiBaseUrl}/api/reports/satisfaction?from=${this.from()}&to=${this.to()}`
    ).pipe(catchError(() => of(null)))
      .subscribe((res) => this.satisfaction.set(res));
  }

  downloadRegisteredClientsPdf(): void {
    this.http.get(`${environment.apiBaseUrl}/api/reports/registered-clients.pdf`, { responseType: 'blob' })
      .subscribe((blob) => this.saveBlob(blob, 'registered-clients.pdf'));
  }

  downloadRegisteredClientsXlsx(): void {
    this.http.get(`${environment.apiBaseUrl}/api/reports/registered-clients.xlsx`, { responseType: 'blob' })
      .subscribe((blob) => this.saveBlob(blob, 'registered-clients.xlsx'));
  }

  downloadTopTherapistsPdf(): void {
    const period = this.topTherapistPeriod();
    this.http.get(`${environment.apiBaseUrl}/api/reports/top-therapists.pdf?period=${period}`, { responseType: 'blob' })
      .subscribe((blob) => this.saveBlob(blob, `top-therapists-${period.toLowerCase()}.pdf`));
  }

  downloadTopTherapistsXlsx(): void {
    const period = this.topTherapistPeriod();
    this.http.get(`${environment.apiBaseUrl}/api/reports/top-therapists.xlsx?period=${period}`, { responseType: 'blob' })
      .subscribe((blob) => this.saveBlob(blob, `top-therapists-${period.toLowerCase()}.xlsx`));
  }

  downloadSatisfactionPdf(): void {
    const url = `${environment.apiBaseUrl}/api/reports/satisfaction.pdf?from=${this.from()}&to=${this.to()}`;
    this.http.get(url, { responseType: 'blob' })
      .subscribe((blob) => this.saveBlob(blob, `satisfaction-${this.from()}-to-${this.to()}.pdf`));
  }

  private saveBlob(blob: Blob, filename: string): void {
    const objectUrl = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = objectUrl;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(objectUrl);
  }

  ngOnDestroy(): void {
    this.trendChart?.destroy();
    this.methodChart?.destroy();
  }

  downloadSalesPdf(): void {
    const url = `${environment.apiBaseUrl}/api/reports/sales-summary.pdf?from=${this.from()}&to=${this.to()}&groupBy=${this.groupBy()}`;
    this.http.get(url, { responseType: 'blob' }).subscribe((blob) => {
      this.saveBlob(blob, `sales-report-${this.from()}-to-${this.to()}.pdf`);
    });
  }

  load(): void {
    this.loading.set(true);
    this.loadPerformance();
    this.loadSatisfaction();
    const from = this.from();
    const to = this.to();
    const method = this.method();
    const methodParam = method && method !== 'ALL' ? `&method=${encodeURIComponent(method)}` : '';
    const trend$ = this.http.get<RevenuePoint[]>(
      `${environment.apiBaseUrl}/api/cashier/ledger/daily-revenue?from=${from}&to=${to}${methodParam}`
    ).pipe(catchError(() => of([] as RevenuePoint[])));
    const breakdown$ = forkJoin(PAYMENT_METHODS.map(m =>
      this.http.get<Balance>(
        `${environment.apiBaseUrl}/api/cashier/ledger/balance?from=${from}&to=${to}&method=${m}`
      ).pipe(catchError(() => of({ inflow: 0, outflow: 0, balance: 0, count: 0 } as Balance)))
    ));

    forkJoin({ trend: trend$, breakdown: breakdown$ }).subscribe({
      next: ({ trend, breakdown }) => {
        this.points.set(trend);
        this.methodSlices.set(PAYMENT_METHODS.map((m, i) => ({ method: m, net: breakdown[i].balance })));
        this.loading.set(false);
        this.renderTrend();
        this.renderMethods();
      },
      error: () => this.loading.set(false)
    });
  }

  private renderTrend(): void {
    const canvas = this.trendCanvas()?.nativeElement;
    if (!canvas) return;
    const rows = this.points();
    const labels = rows.map(p => p.date.slice(5));
    const data = rows.map(p => p.net);
    const primary = this.cssVar('--sumi-primary', '#eda0d8');

    if (this.trendChart) {
      this.trendChart.data.labels = labels;
      this.trendChart.data.datasets[0].data = data;
      this.trendChart.update();
      return;
    }
    this.trendChart = new Chart(canvas, {
      type: 'line',
      data: {
        labels,
        datasets: [{
          label: 'Net revenue',
          data,
          borderColor: primary,
          backgroundColor: this.withAlpha(primary, 0.18),
          fill: true,
          tension: 0.3,
          pointRadius: 3,
          pointHoverRadius: 5
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: { mode: 'index', intersect: false },
        plugins: { legend: { display: false } },
        scales: {
          y: { beginAtZero: true, ticks: { callback: (value) => '₱' + Number(value).toLocaleString() } }
        }
      }
    });
  }

  private renderMethods(): void {
    const canvas = this.methodCanvas()?.nativeElement;
    if (!canvas) return;
    const slices = this.methodSlices();
    const labels = slices.map(s => s.method);
    const data = slices.map(s => s.net);
    const palette = [
      this.cssVar('--sumi-primary', '#eda0d8'),
      this.cssVar('--sumi-secondary', '#e086c7'),
      this.cssVar('--sumi-accent', '#b0d35d'),
      '#94a3b8'
    ];

    if (this.methodChart) {
      this.methodChart.data.labels = labels;
      this.methodChart.data.datasets[0].data = data;
      this.methodChart.update();
      return;
    }
    this.methodChart = new Chart(canvas, {
      type: 'bar',
      data: {
        labels,
        datasets: [{ label: 'Net by method', data, backgroundColor: palette, borderRadius: 6 }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: {
          y: { beginAtZero: true, ticks: { callback: (value) => '₱' + Number(value).toLocaleString() } }
        }
      }
    });
  }

  private cssVar(name: string, fallback: string): string {
    const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
    return value || fallback;
  }

  private withAlpha(color: string, alpha: number): string {
    const hex = color.replace('#', '');
    if (hex.length !== 6) return color;
    const r = parseInt(hex.slice(0, 2), 16);
    const g = parseInt(hex.slice(2, 4), 16);
    const b = parseInt(hex.slice(4, 6), 16);
    return `rgba(${r}, ${g}, ${b}, ${alpha})`;
  }

  private dayOffset(days: number): string {
    const d = new Date();
    d.setDate(d.getDate() + days);
    return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Manila' }).format(d);
  }

  private humanizeKey(key: string): string {
    const spaced = key.replace(/([A-Z])/g, ' $1');
    return spaced.charAt(0).toUpperCase() + spaced.slice(1);
  }
}
