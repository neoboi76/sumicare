import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { BrandingService } from '../../core/branding/branding.service';
import { environment } from '../../../environments/environment';

interface BookingResponse {
  id: string;
  status: string;
}

interface DeckingEntry {
  therapistId: string;
}

interface RoomItem {
  id: string;
  beds: { id: string; occupancy: Record<string, string> }[];
}

@Component({
  selector: 'sumi-dashboard',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './dashboard.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DashboardComponent implements OnInit {
  private http = inject(HttpClient);
  protected auth = inject(AuthService);
  protected branding = inject(BrandingService);

  todaysBookings = signal(0);
  activeSessions = signal(0);
  lineupCount = signal(0);
  occupiedBeds = signal(0);
  totalBeds = signal(0);

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
    const today = new Date().toISOString().slice(0, 10);
    const start = `${today}T00:00:00.000Z`;
    const end = `${today}T23:59:59.999Z`;
    const params = `?from=${encodeURIComponent(start)}&to=${encodeURIComponent(end)}`;

    this.http.get<BookingResponse[]>(`${environment.apiBaseUrl}/api/bookings${params}`).subscribe({
      next: (b) => {
        this.todaysBookings.set(b.length);
        this.activeSessions.set(b.filter(x => x.status === 'ACTIVE').length);
      }
    });

    this.http.get<DeckingEntry[]>(`${environment.apiBaseUrl}/api/decking`).subscribe({
      next: (d) => this.lineupCount.set(d.length)
    });

    this.http.get<RoomItem[]>(`${environment.apiBaseUrl}/api/rooms`).subscribe({
      next: (rooms) => {
        let total = 0;
        let occupied = 0;
        rooms.forEach(r => {
          total += r.beds.length;
          occupied += r.beds.filter(b => b.occupancy['status'] === 'OCCUPIED').length;
        });
        this.totalBeds.set(total);
        this.occupiedBeds.set(occupied);
      }
    });
  }
}
