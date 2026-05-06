import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

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

@Component({
  selector: 'sumi-admin-rooms',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './rooms.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RoomsAdminComponent implements OnInit {
  private http = inject(HttpClient);
  rooms = signal<Room[]>([]);
  beds = signal<Map<string, Bed[]>>(new Map());
  showRoomForm = signal(false);
  bedFormForRoomId = signal<string | null>(null);

  formRoomNumber = '';
  formFloor = 1;
  formRoomType = 'COMMON';
  formRowSegmented = false;

  formBedLabel = '';
  formBedRow = 0;

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.http.get<Room[]>(`${environment.apiBaseUrl}/api/admin/rooms`).subscribe({
      next: (r) => {
        this.rooms.set(r);
        r.forEach(room => this.loadBeds(room.id));
      }
    });
  }

  loadBeds(roomId: string): void {
    this.http.get<Bed[]>(`${environment.apiBaseUrl}/api/admin/rooms/${roomId}/beds`).subscribe({
      next: (b) => {
        const next = new Map(this.beds());
        next.set(roomId, b);
        this.beds.set(next);
      }
    });
  }

  bedsFor(roomId: string): Bed[] {
    return this.beds().get(roomId) ?? [];
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

  deactivateRoom(r: Room): void {
    if (!window.confirm(`Deactivate room ${r.roomNumber}?`)) return;
    this.http.delete(`${environment.apiBaseUrl}/api/admin/rooms/${r.id}`).subscribe({
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

  deactivateBed(b: Bed): void {
    this.http.delete(`${environment.apiBaseUrl}/api/admin/beds/${b.id}`).subscribe({
      next: () => this.loadBeds(b.roomId)
    });
  }
}
