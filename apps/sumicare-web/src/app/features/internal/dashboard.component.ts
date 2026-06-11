import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { BrandingService } from '../../core/branding/branding.service';
import { environment } from '../../../environments/environment';

interface DashboardSummary {
  todayBookings: number;
  activeSessions: number;
  completedSessions: number;
  therapistsInLineup: number;
  bedsOccupied: number;
}

interface RevenuePoint {
  date: string;
  net: number;
  inflow: number;
  outflow: number;
  count: number;
}

@Component({
  selector: 'sumi-dashboard',
  standalone: true,
  imports: [RouterLink, DecimalPipe],
  templateUrl: './dashboard.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DashboardComponent implements OnInit, OnDestroy {
  private http = inject(HttpClient);
  protected auth = inject(AuthService);
  protected branding = inject(BrandingService);

  todaysBookings = signal(0);
  activeSessions = signal(0);
  completedSessions = signal(0);
  lineupCount = signal(0);
  occupiedBeds = signal(0);
  dailyRevenue = signal(0);
  revenueSeries = signal<number[]>([]);
  loading = signal(true);

  private pollHandle: ReturnType<typeof setInterval> | null = null;

  readonly role = computed(() => this.auth.session()?.role ?? '');

  readonly isReceptionist = computed(() =>
    ['RECEPTIONIST', 'MANAGER', 'ADMIN', 'SUPERADMIN'].includes(this.role())
  );
  readonly isManager = computed(() =>
    ['MANAGER', 'ADMIN', 'SUPERADMIN'].includes(this.role())
  );
  readonly isAdmin = computed(() =>
    ['ADMIN', 'SUPERADMIN'].includes(this.role())
  );

  readonly sparklinePoints = computed(() => {
    const series = this.revenueSeries();
    if (series.length < 2) return '';
    const width = 100;
    const height = 28;
    const pad = 2;
    const max = Math.max(...series, 1);
    const min = Math.min(...series, 0);
    const range = max - min || 1;
    return series.map((value, index) => {
      const x = pad + (index / (series.length - 1)) * (width - 2 * pad);
      const y = height - pad - ((value - min) / range) * (height - 2 * pad);
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    }).join(' ');
  });

  ngOnInit(): void {
    this.reload();
    this.pollHandle = setInterval(() => this.reload(), 30_000);
  }

  ngOnDestroy(): void {
    if (this.pollHandle) clearInterval(this.pollHandle);
  }

  reload(): void {
    this.http.get<DashboardSummary>(`${environment.apiBaseUrl}/api/dashboard/summary`).subscribe({
      next: (s) => {
        this.todaysBookings.set(s.todayBookings);
        this.activeSessions.set(s.activeSessions);
        this.completedSessions.set(s.completedSessions);
        this.lineupCount.set(s.therapistsInLineup);
        this.occupiedBeds.set(s.bedsOccupied);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
    if (this.isManager()) {
      this.loadRevenue();
    }
  }

  private loadRevenue(): void {
    const to = this.manilaDate(0);
    const from = this.manilaDate(-13);
    this.http.get<RevenuePoint[]>(
      `${environment.apiBaseUrl}/api/cashier/ledger/daily-revenue?from=${from}&to=${to}`
    ).subscribe({
      next: (points) => {
        this.revenueSeries.set(points.map(p => p.net));
        this.dailyRevenue.set(points.length ? points[points.length - 1].net : 0);
      },
      error: () => {
        this.revenueSeries.set([]);
        this.dailyRevenue.set(0);
      }
    });
  }

  private manilaDate(offsetDays: number): string {
    const date = new Date();
    date.setDate(date.getDate() + offsetDays);
    return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Manila' }).format(date);
  }
}
