import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { LockerLabelPipe } from '../../../shared/pipes/locker-label.pipe';
import { StompService } from '../../../core/realtime/stomp.service';
import { AuthService } from '../../../core/auth/auth.service';

interface BedView {
  id: string;
  label: string;
  rowIndex: number | null;
  occupancy: Record<string, string>;
}

interface RoomView {
  id: string;
  roomNumber: string;
  floor: number | null;
  roomType: string;
  rowSegmented: boolean;
  beds: BedView[];
}

@Component({
  selector: 'sumi-reception',
  standalone: true,
  imports: [LockerLabelPipe],
  templateUrl: './reception.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ReceptionComponent implements OnInit, OnDestroy {
  private http = inject(HttpClient);
  private stomp = inject(StompService);
  private auth = inject(AuthService);
  private roomSubscription: Subscription | null = null;
  rooms = signal<RoomView[]>([]);

  ngOnInit(): void {
    this.reload();
    const orgId = this.auth.organizationId();
    if (!orgId) return;
    try {
      this.roomSubscription = this.stomp.watch<unknown>('/topic/room-updates/' + orgId).subscribe({
        next: () => this.reload(),
        error: () => undefined
      });
    } catch {
      this.roomSubscription = null;
    }
  }

  ngOnDestroy(): void {
    this.roomSubscription?.unsubscribe();
  }

  reload(): void {
    this.http.get<RoomView[]>(`${environment.apiBaseUrl}/api/rooms`).subscribe({
      next: (rooms) => this.rooms.set(rooms),
      error: () => this.rooms.set([])
    });
  }

  bedClass(bed: BedView): string {
    const base = 'rounded-md border p-3 text-sm transition-all duration-300 hover:shadow-sm ';
    const lock = bed.occupancy['genderLock'];
    if (lock === 'M') return base + 'bg-slate-200';
    if (lock === 'F') return base + 'bg-pink-200';
    return base + 'bg-white';
  }

  elapsed(startedAtMillis: string): string {
    const ms = Number(startedAtMillis);
    if (!ms) return '';
    const minutes = Math.floor((Date.now() - ms) / 60000);
    if (minutes < 60) return `${minutes} min`;
    return `${Math.floor(minutes / 60)}h ${minutes % 60}m`;
  }
}
