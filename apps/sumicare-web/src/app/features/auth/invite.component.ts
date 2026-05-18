import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Router, ActivatedRoute, RouterLink } from '@angular/router';
import { environment } from '../../../environments/environment';
import { PasswordInputComponent } from '../../shared/components/password-input/password-input.component';
import { PasswordStrengthComponent } from '../../shared/components/password-strength/password-strength.component';

@Component({
  selector: 'sumi-invite',
  standalone: true,
  imports: [FormsModule, RouterLink, PasswordInputComponent, PasswordStrengthComponent],
  templateUrl: './invite.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class InviteComponent implements OnInit {
  private http = inject(HttpClient);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  password = '';
  confirmPassword = '';
  private token = '';

  submitting = signal(false);
  error = signal<string | null>(null);
  success = signal(false);
  invalidToken = signal(false);

  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      this.token = params['token'] || '';
      if (!this.token) {
        this.invalidToken.set(true);
      }
    });
  }

  submit(): void {
    if (!this.token || this.password !== this.confirmPassword) return;
    this.submitting.set(true);
    this.error.set(null);

    this.http.post(`${environment.apiBaseUrl}/api/auth/invitations/redeem`, {
      token: this.token,
      password: this.password
    }).subscribe({
      next: () => {
        this.submitting.set(false);
        this.success.set(true);
      },
      error: (err) => {
        this.submitting.set(false);
        const msg: string = err?.error?.message || '';
        if (msg.includes('already been used') || msg.includes('expired') || msg.includes('Invalid')) {
          this.invalidToken.set(true);
        } else {
          this.error.set(msg || 'Could not activate account. Please try again.');
        }
      }
    });
  }
}
