import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'sumi-login',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './login.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LoginComponent {
  private auth = inject(AuthService);
  private router = inject(Router);

  username = '';
  password = '';
  busy = signal(false);
  error = signal<string | null>(null);

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
