/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { ConfirmService } from '../../../shared/components/confirm-dialog/confirm.service';
import { PaginatorComponent } from '../../../shared/components/paginator/paginator.component';
import { LockerLabelPipe } from '../../../shared/pipes/locker-label.pipe';
import { StompService } from '../../../core/realtime/stomp.service';
import { AuthService } from '../../../core/auth/auth.service';

interface Room {
  id: string;
  roomNumber: string;
  floor: number | null;
  roomType: string;
  rowSegmented: boolean;
  active: boolean;
}

interface Bed {
  id: string;
  roomId: string;
  bedLabel: string;
  rowIndex: number | null;
  active: boolean;
}

interface OccupancyBed {
  id: string;
  occupancy: Record<string, string>;
}

interface OccupancyRoom {
  id: string;
  beds: OccupancyBed[];
}

@Component({
  selector: 'sumi-admin-rooms',
  standalone: true,
  imports: [FormsModule, PaginatorComponent, LockerLabelPipe],
  templateUrl: './rooms.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RoomsAdminComponent implements OnInit, OnDestroy {
  private http = inject(HttpClient);
  private confirmService = inject(ConfirmService);
  private stomp = inject(StompService);
  private auth = inject(AuthService);

  rooms = signal<Room[]>([]);
  beds = signal<Map<string, Bed[]>>(new Map());
  occupancy = signal<Map<string, Record<string, string>>>(new Map());
  showRoomForm = signal(false);
  bedFormForRoomId = signal<string | null>(null);

  formRoomNumber = '';
  formFloor = 1;
  formRoomType = 'COMMON';
  formRowSegmented = false;

  formBedLabel = '';
  formBedRow = 0;

  currentPage = signal(0);
  pageSize = signal(10);

  private occupancySubscription: Subscription | null = null;
  private occupancyDebounce: ReturnType<typeof setTimeout> | null = null;

  pagedRooms = computed(() => {
    const start = this.currentPage() * this.pageSize();
    return this.rooms().slice(start, start + this.pageSize());
  });

  ngOnInit(): void {
    this.reload();
    this.loadOccupancy();
    this.subscribeOccupancy();
  }

  ngOnDestroy(): void {
    if (this.occupancyDebounce) clearTimeout(this.occupancyDebounce);
    this.occupancySubscription?.unsubscribe();
  }

  reload(): void {
    this.http.get<{ room: Room; beds: Bed[] }[]>(`${environment.apiBaseUrl}/api/admin/rooms/with-beds?includeInactive=true`).subscribe({
      next: (rows) => {
        this.rooms.set(rows.map(r => r.room));
        const next = new Map<string, Bed[]>();
        for (const r of rows) next.set(r.room.id, r.beds);
        this.beds.set(next);
        this.currentPage.set(0);
      }
    });
  }

  loadBeds(roomId: string): void {
    this.http.get<Bed[]>(`${environment.apiBaseUrl}/api/admin/rooms/${roomId}/beds?includeInactive=true`).subscribe({
      next: (b) => {
        const next = new Map(this.beds());
        next.set(roomId, b);
        this.beds.set(next);
      }
    });
  }

  private loadOccupancy(): void {
    this.http.get<OccupancyRoom[]>(`${environment.apiBaseUrl}/api/rooms`).subscribe({
      next: (rooms) => {
        const map = new Map<string, Record<string, string>>();
        for (const room of rooms) {
          for (const bed of room.beds ?? []) {
            map.set(bed.id, bed.occupancy ?? {});
          }
        }
        this.occupancy.set(map);
      },
      error: () => this.occupancy.set(new Map())
    });
  }

  private subscribeOccupancy(): void {
    const orgId = this.auth.organizationId();
    if (!orgId) return;
    try {
      this.occupancySubscription = this.stomp.watch<unknown>('/topic/room-updates/' + orgId).subscribe({
        next: () => this.scheduleOccupancyReload(),
        error: () => undefined
      });
    } catch {
      this.occupancySubscription = null;
    }
  }

  private scheduleOccupancyReload(): void {
    if (this.occupancyDebounce) clearTimeout(this.occupancyDebounce);
    this.occupancyDebounce = setTimeout(() => this.loadOccupancy(), 200);
  }

  bedsFor(roomId: string): Bed[] {
    return this.beds().get(roomId) ?? [];
  }

  bedClass(bed: Bed): string {
    const base = 'rounded-lg border p-2.5 text-xs transition-all duration-300 ';
    if (!bed.active) {
      return base + 'border-slate-200 bg-slate-50 text-slate-400 opacity-70';
    }
    const occ = this.occupancy().get(bed.id);
    if (occ && occ['status'] === 'OCCUPIED') {
      return base + (occ['genderLock'] === 'F'
        ? 'border-pink-200 bg-pink-50 text-pink-900'
        : 'border-slate-300 bg-slate-100 text-slate-900');
    }
    return base + 'border-emerald-200 bg-emerald-50 text-emerald-900';
  }

  bedStateLabel(bed: Bed): string {
    if (!bed.active) return 'Inactive';
    const occ = this.occupancy().get(bed.id);
    if (occ && occ['status'] === 'OCCUPIED') {
      return occ['genderLock'] === 'F' ? 'Female' : 'Male';
    }
    return 'Available';
  }

  occupantNickname(bed: Bed): string | null {
    const occ = this.occupancy().get(bed.id);
    return occ && occ['status'] === 'OCCUPIED' ? (occ['clientNickname'] || null) : null;
  }

  occupantLocker(bed: Bed): string | null {
    const occ = this.occupancy().get(bed.id);
    return occ && occ['status'] === 'OCCUPIED' ? (occ['lockerNumber'] || null) : null;
  }

  occupantGender(bed: Bed): string | null {
    const occ = this.occupancy().get(bed.id);
    return occ ? (occ['genderLock'] || null) : null;
  }

  roomGenderLock(roomId: string): string | null {
    for (const bed of this.bedsFor(roomId)) {
      const occ = this.occupancy().get(bed.id);
      if (occ && occ['status'] === 'OCCUPIED' && occ['genderLock']) {
        return occ['genderLock'];
      }
    }
    return null;
  }

  submitRoom(): void {
    const payload = {
      roomNumber: this.formRoomNumber,
      floor: Number(this.formFloor),
      roomType: this.formRoomType,
      rowSegmented: this.formRowSegmented
    };
    this.http.post(`${environment.apiBaseUrl}/api/admin/rooms`, payload).subscribe({
      next: () => {
        this.showRoomForm.set(false);
        this.formRoomNumber = '';
        this.reload();
      }
    });
  }

  async deactivateRoom(r: Room): Promise<void> {
    const confirmed = await this.confirmService.confirm({
      title: 'Deactivate Room',
      message: `Are you sure you want to deactivate room ${r.roomNumber}?`,
      confirmText: 'Deactivate',
      danger: true
    });
    if (!confirmed) return;
    this.http.delete(`${environment.apiBaseUrl}/api/admin/rooms/${r.id}`).subscribe({
      next: () => this.reload()
    });
  }

  reactivateRoom(r: Room): void {
    this.http.patch(`${environment.apiBaseUrl}/api/admin/rooms/${r.id}/reactivate`, {}).subscribe({
      next: () => this.reload()
    });
  }

  openBedForm(r: Room): void {
    this.formBedLabel = '';
    this.formBedRow = 0;
    this.bedFormForRoomId.set(r.id);
  }

  submitBed(roomId: string): void {
    const payload = { bedLabel: this.formBedLabel, rowIndex: Number(this.formBedRow) };
    this.http.post(`${environment.apiBaseUrl}/api/admin/rooms/${roomId}/beds`, payload).subscribe({
      next: () => {
        this.bedFormForRoomId.set(null);
        this.loadBeds(roomId);
      }
    });
  }

  async deactivateBed(b: Bed): Promise<void> {
    const confirmed = await this.confirmService.confirm({
      title: 'Deactivate Bed',
      message: `Are you sure you want to deactivate bed ${b.bedLabel}?`,
      confirmText: 'Deactivate',
      danger: true
    });
    if (!confirmed) return;
    this.http.delete(`${environment.apiBaseUrl}/api/admin/beds/${b.id}`).subscribe({
      next: () => this.loadBeds(b.roomId)
    });
  }

  reactivateBed(b: Bed): void {
    this.http.patch(`${environment.apiBaseUrl}/api/admin/beds/${b.id}/reactivate`, {}).subscribe({
      next: () => this.loadBeds(b.roomId)
    });
  }
}
