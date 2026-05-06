import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

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

  deactivate(s: Shift): void {
    if (!window.confirm(`Deactivate ${s.label}?`)) return;
    this.http.delete(`${environment.apiBaseUrl}/api/shifts/${s.id}`).subscribe({
      next: () => this.reload()
    });
  }
}
