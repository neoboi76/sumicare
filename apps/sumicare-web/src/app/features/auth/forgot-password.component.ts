import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'sumi-forgot-password',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <div class="min-h-screen bg-slate-50 flex flex-col justify-center py-12 sm:px-6 lg:px-8">
      <div class="sm:mx-auto sm:w-full sm:max-w-md">
        <h2 class="mt-6 text-center text-3xl font-extrabold text-slate-900">
          Forgot your password?
        </h2>
        <p class="mt-2 text-center text-sm text-slate-600">
          Enter your email and we'll send you a reset link.
        </p>
      </div>

      <div class="mt-8 sm:mx-auto sm:w-full sm:max-w-md">
        <div class="bg-white py-8 px-4 shadow sm:rounded-lg sm:px-10">
          @if (sent()) {
            <div class="rounded-md bg-emerald-50 p-4">
              <h3 class="text-sm font-medium text-emerald-800">Check your email</h3>
              <p class="mt-2 text-sm text-emerald-700">
                If your email address is associated with an account, a password reset link has been sent. The link expires in 1 hour.
              </p>
              <div class="mt-4">
                <a routerLink="/login" class="text-sm font-medium text-emerald-800 hover:text-emerald-700">
                  Back to Login &rarr;
                </a>
              </div>
            </div>
          } @else {
            <form class="space-y-5" (ngSubmit)="submit()">
              @if (error()) {
                <div class="rounded-md bg-rose-50 p-4">
                  <p class="text-sm font-medium text-rose-800">{{ error() }}</p>
                </div>
              }

              <div>
                <label for="email" class="block text-sm font-medium text-slate-700">
                  Email address
                </label>
                <div class="mt-1">
                  <input
                    id="email"
                    name="email"
                    type="email"
                    required
                    autocomplete="email"
                    [(ngModel)]="email"
                    class="appearance-none block w-full px-3 py-2 border border-slate-300 rounded-md shadow-sm placeholder-slate-400 focus:outline-none focus:ring-[var(--sumi-primary)] focus:border-[var(--sumi-primary)] sm:text-sm"
                    placeholder="you@example.com"
                  />
                </div>
              </div>

              <div>
                <button
                  type="submit"
                  [disabled]="submitting() || !email"
                  class="w-full flex justify-center py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-[var(--sumi-primary)] hover:opacity-90 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-[var(--sumi-primary)] disabled:opacity-50 disabled:cursor-not-allowed">
                  {{ submitting() ? 'Sending...' : 'Send Reset Link' }}
                </button>
              </div>

              <div class="text-center">
                <a routerLink="/login" class="text-sm text-slate-500 hover:text-slate-700">
                  Back to Login
                </a>
              </div>
            </form>
          }
        </div>
      </div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ForgotPasswordComponent {
  private http = inject(HttpClient);

  email = '';

  submitting = signal(false);
  error = signal<string | null>(null);
  sent = signal(false);

  submit(): void {
    if (!this.email) return;
    this.submitting.set(true);
    this.error.set(null);

    this.http.post(`${environment.apiBaseUrl}/api/auth/password-reset/request`, {
      email: this.email
    }).subscribe({
      next: () => {
        this.submitting.set(false);
        this.sent.set(true);
      },
      error: () => {
        this.submitting.set(false);
        this.sent.set(true);
      }
    });
  }
}
