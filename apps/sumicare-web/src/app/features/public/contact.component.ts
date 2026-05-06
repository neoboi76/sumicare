import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-contact',
  templateUrl: './contact.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule]
})
export class ContactComponent {
  private http = inject(HttpClient);

  name = signal('');
  email = signal('');
  message = signal('');
  submitting = signal(false);
  success = signal(false);
  error = signal('');

  submit(): void {
    if (this.submitting()) return;
    this.submitting.set(true);
    this.error.set('');
    this.http.post(`${environment.apiBaseUrl}/api/public/feedback`, {
      nickname: this.name() || 'Anonymous',
      message: this.message(),
      contactEmail: this.email(),
      category: 'CONTACT'
    }).subscribe({
      next: () => { this.success.set(true); this.submitting.set(false); },
      error: () => { this.error.set('Could not send message. Please try again.'); this.submitting.set(false); }
    });
  }
}
