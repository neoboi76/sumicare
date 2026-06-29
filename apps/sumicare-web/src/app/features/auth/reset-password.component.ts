/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { PasswordInputComponent } from '../../shared/components/password-input/password-input.component';
import { PasswordStrengthComponent } from '../../shared/components/password-strength/password-strength.component';
import { ActivatedRoute, RouterLink } from '@angular/router';

@Component({
  selector: 'sumi-reset-password',
  standalone: true,
  imports: [FormsModule, PasswordInputComponent, PasswordStrengthComponent, RouterLink],
  templateUrl: './reset-password.component.html',
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
