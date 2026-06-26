/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { ConfirmService } from '../../../shared/components/confirm-dialog/confirm.service';
import { SortableColumnDirective } from '../../../shared/directives/sortable-column.directive';
import { SortIconComponent } from '../../../shared/components/sort-icon/sort-icon.component';
import { SortState, sortRows } from '../../../shared/utils/compare-by';

interface Therapist {
  id: string;
  staffNumber: string | null;
  nickname: string;
  gender: string;
  backup: boolean;
  active: boolean;
  currentShiftId: number | null;
  currentShiftLabel: string | null;
}

interface Shift {
  id: number;
  label: string;
  startTime: string;
  endTime: string;
}

@Component({
  selector: 'sumi-admin-therapists',
  standalone: true,
  imports: [FormsModule, SortableColumnDirective, SortIconComponent],
  templateUrl: './therapists.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class TherapistsAdminComponent implements OnInit {
  private http = inject(HttpClient);
  private confirmService = inject(ConfirmService);
  therapists = signal<Therapist[]>([]);
  deactivated = signal<Therapist[]>([]);
  shifts = signal<Shift[]>([]);
  showForm = signal(false);
  editingTherapist = signal<Therapist | null>(null);
  formError = signal<string | null>(null);

  sortState = signal<SortState>({ key: 'nickname', direction: 'asc' });
  genderFilter = signal<'ALL' | 'M' | 'F'>('ALL');
  shiftFilter = signal<number | 'ALL'>('ALL');

  sortedTherapists = computed(() => {
    const state = this.sortState();
    return sortRows(this.therapists(), state, (t) => {
      switch (state.key) {
        case 'staffNumber': return t.staffNumber ?? '';
        case 'nickname': return t.nickname;
        case 'gender': return t.gender;
        case 'type': return t.backup ? 'Backup' : 'Regular';
        case 'shift': return t.currentShiftLabel ?? '';
        default: return '';
      }
    });
  });

  filteredTherapists = computed(() => {
    const gender = this.genderFilter();
    const shift = this.shiftFilter();
    return this.sortedTherapists().filter((t) => {
      if (gender !== 'ALL' && t.gender !== gender) return false;
      if (shift !== 'ALL' && t.currentShiftId !== shift) return false;
      return true;
    });
  });

  groupedByShift = computed(() => {
    const groups = new Map<string, Therapist[]>();
    for (const t of this.filteredTherapists()) {
      const label = t.currentShiftLabel ?? 'Unassigned';
      const list = groups.get(label) ?? [];
      list.push(t);
      groups.set(label, list);
    }
    return Array.from(groups, ([label, items]) => ({ label, items }));
  });

  formStaffNumber = '';
  formNickname = '';
  formGender = 'F';
  formBackup = false;
  formShiftId: number | null = null;

  ngOnInit(): void {
    this.reload();
    this.http.get<Shift[]>(`${environment.apiBaseUrl}/api/shifts`).subscribe({
      next: (s) => this.shifts.set(s),
      error: () => this.shifts.set([])
    });
  }

  reload(): void {
    this.http.get<Therapist[]>(`${environment.apiBaseUrl}/api/therapists`).subscribe({
      next: (t) => this.therapists.set(t)
    });
    this.http.get<Therapist[]>(`${environment.apiBaseUrl}/api/therapists/deactivated`).subscribe({
      next: (t) => this.deactivated.set(t),
      error: () => this.deactivated.set([])
    });
  }

  openCreate(): void {
    this.editingTherapist.set(null);
    this.formStaffNumber = '';
    this.formNickname = '';
    this.formGender = 'F';
    this.formBackup = false;
    this.formShiftId = null;
    this.formError.set(null);
    this.showForm.set(true);
  }

  openEdit(t: Therapist): void {
    this.editingTherapist.set(t);
    this.formStaffNumber = t.staffNumber || '';
    this.formNickname = t.nickname;
    this.formGender = t.gender;
    this.formBackup = t.backup;
    this.formShiftId = t.currentShiftId;
    this.formError.set(null);
    this.showForm.set(true);
  }

  submit(): void {
    const payload = {
      staffNumber: this.formStaffNumber || null,
      nickname: this.formNickname,
      gender: this.formGender,
      backup: this.formBackup,
      shiftId: this.formShiftId
    };

    const editing = this.editingTherapist();
    const req$ = editing
      ? this.http.patch(`${environment.apiBaseUrl}/api/therapists/${editing.id}`, payload)
      : this.http.post(`${environment.apiBaseUrl}/api/therapists`, payload);

    req$.subscribe({
      next: () => {
        this.showForm.set(false);
        this.formError.set(null);
        this.reload();
      },
      error: (err) => {
        this.formError.set(err?.error?.message || 'Could not save therapist.');
      }
    });
  }

  async deactivate(t: Therapist): Promise<void> {
    const confirmed = await this.confirmService.confirm({
      title: 'Deactivate Therapist',
      message: `Are you sure you want to deactivate ${t.nickname}?`,
      confirmText: 'Deactivate',
      danger: true
    });
    if (!confirmed) return;
    
    this.http.delete(`${environment.apiBaseUrl}/api/therapists/${t.id}`).subscribe({
      next: () => this.reload()
    });
  }

  reactivate(t: Therapist): void {
    this.http.post(`${environment.apiBaseUrl}/api/therapists/${t.id}/reactivate`, {}).subscribe({
      next: () => this.reload()
    });
  }
}
