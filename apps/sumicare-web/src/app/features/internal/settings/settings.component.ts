import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { ConfirmService } from '../../../shared/components/confirm-dialog/confirm.service';

@Component({
  selector: 'sumi-settings',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './settings.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SettingsComponent implements OnInit {
  private http = inject(HttpClient);
  private confirmService = inject(ConfirmService);

  displayName = '';
  email = '';
  
  savingProfile = signal(false);
  profileSuccess = signal(false);
  profileError = signal<string | null>(null);

  requestingReset = signal(false);
  resetSuccess = signal(false);
  resetError = signal<string | null>(null);

  ngOnInit(): void {
    this.http.get<{ displayName: string; email: string }>(`${environment.apiBaseUrl}/api/users/me`).subscribe({
      next: (u) => {
        this.displayName = u.displayName || '';
        this.email = u.email || '';
      }
    });
  }

  saveProfile(): void {
    this.savingProfile.set(true);
    this.profileSuccess.set(false);
    this.profileError.set(null);
    
    this.http.patch(`${environment.apiBaseUrl}/api/users/me/profile`, { displayName: this.displayName }).subscribe({
      next: () => {
        this.savingProfile.set(false);
        this.profileSuccess.set(true);
        setTimeout(() => this.profileSuccess.set(false), 3000);
      },
      error: (err) => {
        this.savingProfile.set(false);
        this.profileError.set(err?.error?.message || 'Failed to update profile');
      }
    });
  }

  async requestPasswordReset(): Promise<void> {
    const confirmed = await this.confirmService.confirm({
      title: 'Password Reset',
      message: 'Send a password reset link to your email?',
      confirmText: 'Send Email'
    });
    if (!confirmed) return;
    
    this.requestingReset.set(true);
    this.resetSuccess.set(false);
    this.resetError.set(null);
    
    this.http.post(`${environment.apiBaseUrl}/api/users/me/request-password-reset`, {}).subscribe({
      next: () => {
        this.requestingReset.set(false);
        this.resetSuccess.set(true);
        setTimeout(() => this.resetSuccess.set(false), 5000);
      },
      error: (err) => {
        this.requestingReset.set(false);
        this.resetError.set(err?.error?.message || 'Failed to request password reset');
      }
    });
  }
}
