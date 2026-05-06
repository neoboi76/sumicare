import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

interface Therapist {
  id: string;
  staffNumber: string | null;
  nickname: string;
  gender: string;
  backup: boolean;
  active: boolean;
}

@Component({
  selector: 'sumi-admin-therapists',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './therapists.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class TherapistsAdminComponent implements OnInit {
  private http = inject(HttpClient);
  therapists = signal<Therapist[]>([]);
  showForm = signal(false);

  formStaffNumber = '';
  formNickname = '';
  formGender = 'F';
  formBackup = false;

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.http.get<Therapist[]>(`${environment.apiBaseUrl}/api/therapists`).subscribe({
      next: (t) => this.therapists.set(t)
    });
  }

  submit(): void {
    const payload = {
      staffNumber: this.formStaffNumber || null,
      nickname: this.formNickname,
      gender: this.formGender,
      backup: this.formBackup
    };
    this.http.post(`${environment.apiBaseUrl}/api/therapists`, payload).subscribe({
      next: () => {
        this.showForm.set(false);
        this.formStaffNumber = '';
        this.formNickname = '';
        this.formBackup = false;
        this.reload();
      }
    });
  }

  deactivate(t: Therapist): void {
    if (!window.confirm(`Deactivate ${t.nickname}?`)) return;
    this.http.delete(`${environment.apiBaseUrl}/api/therapists/${t.id}`).subscribe({
      next: () => this.reload()
    });
  }
}
