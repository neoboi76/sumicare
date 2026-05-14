import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { PasswordStrengthComponent } from '../../shared/components/password-strength/password-strength.component';
import { ActivatedRoute, RouterLink } from '@angular/router';

@Component({
  selector: 'sumi-reset-password',
  standalone: true,
  imports: [FormsModule, PasswordStrengthComponent, RouterLink],
  template: `
    <div class="min-h-screen bg-slate-50 flex flex-col justify-center py-12 sm:px-6 lg:px-8">
      <div class="sm:mx-auto sm:w-full sm:max-w-md">
        <h2 class="mt-6 text-center text-3xl font-extrabold text-slate-900">
          Reset your password
        </h2>
      </div>

      <div class="mt-8 sm:mx-auto sm:w-full sm:max-w-md">
        <div class="bg-white py-8 px-4 shadow sm:rounded-lg sm:px-10">
          @if (success()) {
            <div class="rounded-md bg-emerald-50 p-4 mb-6">
              <div class="flex">
                <div class="ml-3">
                  <h3 class="text-sm font-medium text-emerald-800">Password reset successful</h3>
                  <div class="mt-2 text-sm text-emerald-700">
                    <p>Your password has been successfully reset. You may now log in with your new password.</p>
                  </div>
                  <div class="mt-4">
                    <a routerLink="/login" class="text-sm font-medium text-emerald-800 hover:text-emerald-700">
                      Go to Login &rarr;
                    </a>
                  </div>
                </div>
              </div>
            </div>
          } @else {
            <form class="space-y-6" (ngSubmit)="submit()">
              @if (error()) {
                <div class="rounded-md bg-rose-50 p-4">
                  <div class="flex">
                    <div class="ml-3">
                      <h3 class="text-sm font-medium text-rose-800">{{ error() }}</h3>
                    </div>
                  </div>
                </div>
              }

              <div>
                <label for="password" class="block text-sm font-medium text-slate-700">
                  New Password
                </label>
                <div class="mt-1">
                  <input id="password" name="password" type="password" required [(ngModel)]="password"
                         class="appearance-none block w-full px-3 py-2 border border-slate-300 rounded-md shadow-sm placeholder-slate-400 focus:outline-none focus:ring-[var(--sumi-primary)] focus:border-[var(--sumi-primary)] sm:text-sm">
                </div>
                <sumi-password-strength [password]="password"></sumi-password-strength>
              </div>

              <div>
                <label for="confirmPassword" class="block text-sm font-medium text-slate-700">
                  Confirm New Password
                </label>
                <div class="mt-1">
                  <input id="confirmPassword" name="confirmPassword" type="password" required [(ngModel)]="confirmPassword"
                         class="appearance-none block w-full px-3 py-2 border border-slate-300 rounded-md shadow-sm placeholder-slate-400 focus:outline-none focus:ring-[var(--sumi-primary)] focus:border-[var(--sumi-primary)] sm:text-sm">
                </div>
              </div>

              <div>
                <button type="submit" [disabled]="submitting() || !password || password !== confirmPassword"
                        class="w-full flex justify-center py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-[var(--sumi-primary)] hover:opacity-90 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-[var(--sumi-primary)] disabled:opacity-50 disabled:cursor-not-allowed">
                  {{ submitting() ? 'Saving...' : 'Reset Password' }}
                </button>
              </div>
            </form>
          }
        </div>
      </div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ResetPasswordComponent implements OnInit {
  private http = inject(HttpClient);
  private route = inject(ActivatedRoute);

  password = '';
  confirmPassword = '';
  token = '';

  submitting = signal(false);
  error = signal<string | null>(null);
  success = signal(false);

  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      this.token = params['token'] || '';
      if (!this.token) {
        this.error.set('Invalid or missing reset token.');
      }
    });
  }

  submit(): void {
    if (!this.token) return;
    if (this.password !== this.confirmPassword) {
      this.error.set('Passwords do not match.');
      return;
    }
    
    this.submitting.set(true);
    this.error.set(null);
    
    this.http.post(`${environment.apiBaseUrl}/api/auth/reset-password`, {
      token: this.token,
      newPassword: this.password
    }).subscribe({
      next: () => {
        this.submitting.set(false);
        this.success.set(true);
      },
      error: (err) => {
        this.submitting.set(false);
        this.error.set(err?.error?.message || 'Could not reset password. The link may be expired.');
      }
    });
  }
}
