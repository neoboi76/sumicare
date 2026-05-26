import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { PasswordInputComponent } from '../../shared/components/password-input/password-input.component';
import { ContactAdminModalComponent } from './contact-admin-modal/contact-admin-modal.component';

@Component({
  selector: 'sumi-login',
  standalone: true,
  imports: [FormsModule, PasswordInputComponent, ContactAdminModalComponent],
  templateUrl: './login.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LoginComponent implements OnInit {
  private auth = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  username = '';
  password = '';
  code = '';
  busy = signal(false);
  error = signal<string | null>(null);
  verifiedBanner = signal<'success' | 'expired' | null>(null);
  idleBanner = signal(false);
  showContactModal = signal(false);
  mfaRequired = signal(false);
  maskedEmail = signal<string | null>(null);
  resendNotice = signal(false);
  private challengeId: string | null = null;

  openContactModal(): void { this.showContactModal.set(true); }
  closeContactModal(): void { this.showContactModal.set(false); }

  ngOnInit(): void {
    const verified = this.route.snapshot.queryParamMap.get('verified');
    if (verified === '1') this.verifiedBanner.set('success');
    else if (verified === 'expired') this.verifiedBanner.set('expired');
    const reason = this.route.snapshot.queryParamMap.get('reason');
    if (reason === 'idle') this.idleBanner.set(true);
  }

  submit(event: Event): void {
    event.preventDefault();
    this.busy.set(true);
    this.error.set(null);
    this.auth.login(this.username, this.password).subscribe({
      next: (response) => {
        this.busy.set(false);
        if (response.mfaRequired && response.challengeId) {
          this.challengeId = response.challengeId;
          this.maskedEmail.set(response.email);
          this.mfaRequired.set(true);
          return;
        }
        this.router.navigate(['/app/dashboard']);
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(this.messageFor(err));
      }
    });
  }

  verify(event: Event): void {
    event.preventDefault();
    if (!this.challengeId) return;
    this.busy.set(true);
    this.error.set(null);
    this.auth.verifyMfa(this.challengeId, this.code.trim()).subscribe({
      next: () => {
        this.busy.set(false);
        this.router.navigate(['/app/dashboard']);
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(this.messageFor(err));
      }
    });
  }

  resend(): void {
    if (!this.challengeId || this.busy()) return;
    this.busy.set(true);
    this.error.set(null);
    this.resendNotice.set(false);
    this.auth.resendMfa(this.challengeId).subscribe({
      next: () => {
        this.busy.set(false);
        this.resendNotice.set(true);
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(this.messageFor(err));
      }
    });
  }

  backToSignIn(): void {
    this.mfaRequired.set(false);
    this.challengeId = null;
    this.code = '';
    this.error.set(null);
    this.resendNotice.set(false);
    this.maskedEmail.set(null);
  }

  private messageFor(err: { status?: number; error?: { message?: string } }): string {
    if (err?.error?.message) return err.error.message;
    if (err?.status === 401 || err?.status === 400) return 'Invalid credentials';
    return 'Sign-in failed';
  }
}
