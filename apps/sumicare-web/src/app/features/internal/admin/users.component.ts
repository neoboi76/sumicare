import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

interface UserRow {
  id: string;
  username: string;
  email: string | null;
  role: string;
  displayName: string | null;
  active: boolean;
}

@Component({
  selector: 'sumi-users',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './users.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class UsersComponent implements OnInit {
  private http = inject(HttpClient);
  users = signal<UserRow[]>([]);
  showForm = signal(false);
  error = signal<string | null>(null);

  formUsername = '';
  formEmail = '';
  formDisplayName = '';
  formPassword = '';
  formRole = 'RECEPTIONIST';

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.http.get<UserRow[]>(`${environment.apiBaseUrl}/api/users`).subscribe({
      next: (rows) => this.users.set(rows),
      error: () => this.users.set([])
    });
  }

  submit(): void {
    this.error.set(null);
    const payload = {
      username: this.formUsername,
      email: this.formEmail || undefined,
      displayName: this.formDisplayName || undefined,
      password: this.formPassword,
      role: this.formRole
    };
    this.http.post(`${environment.apiBaseUrl}/api/users`, payload).subscribe({
      next: () => {
        this.showForm.set(false);
        this.formUsername = '';
        this.formEmail = '';
        this.formDisplayName = '';
        this.formPassword = '';
        this.reload();
      },
      error: (e) => this.error.set(e.error?.message || 'Failed to create user')
    });
  }
}
