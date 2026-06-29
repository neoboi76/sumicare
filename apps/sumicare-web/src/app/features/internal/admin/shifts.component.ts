/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { ConfirmService } from '../../../shared/components/confirm-dialog/confirm.service';

interface Shift {
  id: number;
  label: string;
  startTime: string;
  endTime: string;
  expectedCount: number | null;
  active: boolean;
}

@Component({
  selector: 'sumi-admin-shifts',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './shifts.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ShiftsAdminComponent implements OnInit {
  private http = inject(HttpClient);
  private confirmService = inject(ConfirmService);
  shifts = signal<Shift[]>([]);
  showForm = signal(false);

  formLabel = '';
  formStart = '07:00';
  formEnd = '17:00';
  formExpected = 10;

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.http.get<Shift[]>(`${environment.apiBaseUrl}/api/shifts`).subscribe({
      next: (s) => this.shifts.set(s)
    });
  }

  submit(): void {
    const payload = {
      label: this.formLabel,
      startTime: this.formStart + ':00',
      endTime: this.formEnd + ':00',
      expectedCount: Number(this.formExpected)
    };
    this.http.post(`${environment.apiBaseUrl}/api/shifts`, payload).subscribe({
      next: () => {
        this.showForm.set(false);
        this.formLabel = '';
        this.reload();
      }
    });
  }

  async deactivate(s: Shift): Promise<void> {
    const confirmed = await this.confirmService.confirm({
      title: 'Deactivate Shift',
      message: `Are you sure you want to deactivate "${s.label}"?`,
      confirmText: 'Deactivate',
      danger: true
    });
    if (!confirmed) return;
    this.http.delete(`${environment.apiBaseUrl}/api/shifts/${s.id}`).subscribe({
      next: () => this.reload()
    });
  }
}
