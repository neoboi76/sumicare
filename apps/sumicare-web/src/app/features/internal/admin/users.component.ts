import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { AuthService } from '../../../core/auth/auth.service';
import { ConfirmService } from '../../../shared/components/confirm-dialog/confirm.service';
import { UserAuditDrawerComponent } from './user-audit-drawer.component';
import { SortableColumnDirective } from '../../../shared/directives/sortable-column.directive';
import { SortIconComponent } from '../../../shared/components/sort-icon/sort-icon.component';
import { SortState, sortRows } from '../../../shared/utils/compare-by';

interface UserRow {
  id: string;
  username: string;
  email: string | null;
  role: string;
  displayName: string | null;
  active: boolean;
  accountLocked: boolean;
}

@Component({
  selector: 'sumi-users',
  standalone: true,
  imports: [FormsModule, UserAuditDrawerComponent, SortableColumnDirective, SortIconComponent],
  templateUrl: './users.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class UsersComponent implements OnInit {
  private http = inject(HttpClient);
  private auth = inject(AuthService);
  private confirmService = inject(ConfirmService);

  users = signal<UserRow[]>([]);
  deactivated = signal<UserRow[]>([]);
  resetSent = signal<string | null>(null);
  showForm = signal(false);
  error = signal<string | null>(null);

  auditUserId = signal<string | null>(null);
  auditUsername = signal('');

  sortState = signal<SortState>({ key: 'username', direction: 'asc' });

  sortedUsers = computed(() => sortRows(this.users(), this.sortState(), (u) => {
    switch (this.sortState().key) {
      case 'username': return u.username;
      case 'displayName': return u.displayName ?? '';
      case 'email': return u.email ?? '';
      case 'role': return u.role;
      default: return '';
    }
  }));

  myRole = computed(() => this.auth.session()?.role ?? '');
  canManage = computed(() => this.myRole() === 'SUPERADMIN' || this.myRole() === 'ADMIN');

  canManageUser(targetRole: string): boolean {
    const r = this.myRole();
    if (r === 'SUPERADMIN') return targetRole !== 'SUPERADMIN';
    if (r === 'ADMIN') return ['MANAGER', 'RECEPTIONIST'].includes(targetRole);
    return false;
  }

  allowedRoles = computed<string[]>(() => {
    if (this.myRole() === 'SUPERADMIN') return ['RECEPTIONIST', 'MANAGER', 'ADMIN'];
    return ['RECEPTIONIST', 'MANAGER'];
  });

  formUsername = '';
  formEmail = '';
  formDisplayName = '';
  formRole = 'RECEPTIONIST';

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.http.get<UserRow[]>(`${environment.apiBaseUrl}/api/users`).subscribe({
      next: (rows) => this.users.set(rows),
      error: () => this.users.set([])
    });
    if (this.canManage()) {
      this.http.get<UserRow[]>(`${environment.apiBaseUrl}/api/users/deactivated`).subscribe({
        next: (rows) => this.deactivated.set(rows),
        error: () => this.deactivated.set([])
      });
    }
  }

  submit(): void {
    this.error.set(null);
    const payload = {
      username: this.formUsername,
      email: this.formEmail || undefined,
      displayName: this.formDisplayName || undefined,
      role: this.formRole
    };
    this.http.post(`${environment.apiBaseUrl}/api/users`, payload).subscribe({
      next: () => {
        this.showForm.set(false);
        this.formUsername = '';
        this.formEmail = '';
        this.formDisplayName = '';
        this.reload();
      },
      error: (e) => this.error.set(e.error?.message || 'Failed to create user')
    });
  }

  async deactivate(user: UserRow): Promise<void> {
    const confirmed = await this.confirmService.confirm({
      title: 'Deactivate user',
      message: `Deactivate ${user.displayName || user.username}? They will be signed out immediately and cannot log back in.`,
      confirmText: 'Deactivate',
      danger: true
    });
    if (!confirmed) return;
    this.http.delete(`${environment.apiBaseUrl}/api/users/${user.id}`).subscribe({
      next: () => this.reload(),
      error: (e) => this.error.set(e.error?.message || 'Could not deactivate user.')
    });
  }

  reactivate(user: UserRow): void {
    this.http.post(`${environment.apiBaseUrl}/api/users/${user.id}/reactivate`, {}).subscribe({
      next: () => this.reload(),
      error: (e) => this.error.set(e.error?.message || 'Could not reactivate user.')
    });
  }

  async unlock(user: UserRow): Promise<void> {
    const confirmed = await this.confirmService.confirm({
      title: 'Unlock account',
      message: `Unlock ${user.displayName || user.username}? Their failed sign-in count will be reset and they can sign in again.`,
      confirmText: 'Unlock'
    });
    if (!confirmed) return;
    this.http.post(`${environment.apiBaseUrl}/api/users/${user.id}/unlock`, {}).subscribe({
      next: () => this.reload(),
      error: (e) => this.error.set(e.error?.message || 'Could not unlock user.')
    });
  }

  openAudit(user: UserRow): void {
    this.auditUserId.set(user.id);
    this.auditUsername.set(user.displayName || user.username);
  }

  closeAudit(): void {
    this.auditUserId.set(null);
  }

  async sendResetLink(user: UserRow): Promise<void> {
    const confirmed = await this.confirmService.confirm({
      title: 'Send password reset link',
      message: `Send a password reset link to ${user.displayName || user.username} at ${user.email || '(no email)'}?`,
      confirmText: 'Send link'
    });
    if (!confirmed) return;
    this.http.post(`${environment.apiBaseUrl}/api/users/${user.id}/send-reset-link`, {}).subscribe({
      next: () => this.resetSent.set(`Reset link emailed to ${user.email}`),
      error: (e) => this.error.set(e.error?.message || 'Could not send reset link.')
    });
  }
}
