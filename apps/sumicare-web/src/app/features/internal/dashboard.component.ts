import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { BrandingService } from '../../core/branding/branding.service';
import { environment } from '../../../environments/environment';

interface DashboardSummary {
  todayBookings: number;
  activeSessions: number;
  therapistsInLineup: number;
  bedsOccupied: number;
  cashOnHand: number;
}

@Component({
  selector: 'sumi-dashboard',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './dashboard.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DashboardComponent implements OnInit, OnDestroy {
  private http = inject(HttpClient);
  protected auth = inject(AuthService);
  protected branding = inject(BrandingService);

  todaysBookings = signal(0);
  activeSessions = signal(0);
  lineupCount = signal(0);
  occupiedBeds = signal(0);
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
        this.lineupCount.set(s.therapistsInLineup);
        this.occupiedBeds.set(s.bedsOccupied);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }
}
