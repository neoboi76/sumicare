import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'sumi-login',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './login.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LoginComponent implements OnInit {
  private auth = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  username = '';
  password = '';
  busy = signal(false);
  error = signal<string | null>(null);
  verifiedBanner = signal<'success' | 'expired' | null>(null);

  ngOnInit(): void {
    const verified = this.route.snapshot.queryParamMap.get('verified');
    if (verified === '1') this.verifiedBanner.set('success');
    else if (verified === 'expired') this.verifiedBanner.set('expired');
  }

  submit(event: Event): void {
    event.preventDefault();
    this.busy.set(true);
    this.error.set(null);
    this.auth.login(this.username, this.password).subscribe({
      next: () => {
        this.busy.set(false);
        this.router.navigate(['/app/dashboard']);
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(err.status === 401 || err.status === 400 ? 'Invalid credentials' : 'Sign-in failed');
      }
    });
  }
}
