import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { ConfirmService } from '../../../shared/components/confirm-dialog/confirm.service';

@Component({
  selector: 'sumi-settings',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="p-6 max-w-2xl mx-auto">
      <div class="mb-8">
        <h1 class="text-2xl font-bold text-slate-900">Account Settings</h1>
        <p class="text-sm text-slate-500 mt-1">Manage your personal profile and account security.</p>
      </div>

      <div class="bg-white shadow rounded-lg mb-6">
        <div class="px-6 py-5 border-b border-slate-200">
          <h3 class="text-lg font-medium leading-6 text-slate-900">Profile Information</h3>
        </div>
        <div class="px-6 py-5">
          <form (ngSubmit)="saveProfile()">
            <div class="mb-4">
              <label for="displayName" class="block text-sm font-medium text-slate-700">Display Name</label>
              <input type="text" id="displayName" name="displayName" [(ngModel)]="displayName"
                     class="mt-1 block w-full px-3 py-2 border border-slate-300 rounded-md shadow-sm focus:outline-none focus:ring-[var(--sumi-primary)] focus:border-[var(--sumi-primary)] sm:text-sm">
              <p class="mt-1 text-xs text-slate-500">This name will be displayed on receipts and internal dashboards.</p>
            </div>

            <div class="mb-4">
              <label class="block text-sm font-medium text-slate-700">Email</label>
              <input type="text" [value]="email" disabled
                     class="mt-1 block w-full px-3 py-2 border border-slate-200 rounded-md bg-slate-50 text-slate-500 sm:text-sm cursor-not-allowed">
              <p class="mt-1 text-xs text-slate-500">Contact an administrator to change your email address.</p>
            </div>
            
            <div class="flex items-center justify-between">
              @if (profileSuccess()) {
                <span class="text-sm text-emerald-600 font-medium">Profile updated successfully!</span>
              } @else if (profileError()) {
                <span class="text-sm text-rose-600 font-medium">{{ profileError() }}</span>
              } @else {
                <span></span>
              }
              <button type="submit" [disabled]="savingProfile()"
                      class="inline-flex justify-center py-2 px-4 border border-transparent shadow-sm text-sm font-medium rounded-md text-white bg-[var(--sumi-primary)] hover:opacity-90 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-[var(--sumi-primary)] disabled:opacity-50">
                {{ savingProfile() ? 'Saving...' : 'Save Profile' }}
              </button>
            </div>
          </form>
        </div>
      </div>

      <div class="bg-white shadow rounded-lg">
        <div class="px-6 py-5 border-b border-slate-200">
          <h3 class="text-lg font-medium leading-6 text-slate-900">Security</h3>
        </div>
        <div class="px-6 py-5">
          <p class="text-sm text-slate-600 mb-4">
            If you need to change your password, you can request a password reset link. It will be sent to the email address associated with your account.
          </p>
          
          <div class="flex items-center justify-between">
            @if (resetSuccess()) {
              <span class="text-sm text-emerald-600 font-medium">Reset link sent to your email!</span>
            } @else if (resetError()) {
              <span class="text-sm text-rose-600 font-medium">{{ resetError() }}</span>
            } @else {
              <span></span>
            }
            <button type="button" (click)="requestPasswordReset()" [disabled]="requestingReset()"
                    class="inline-flex justify-center py-2 px-4 border border-slate-300 shadow-sm text-sm font-medium rounded-md text-slate-700 bg-white hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-[var(--sumi-primary)] disabled:opacity-50">
              {{ requestingReset() ? 'Sending...' : 'Request Password Reset' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  `,
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
