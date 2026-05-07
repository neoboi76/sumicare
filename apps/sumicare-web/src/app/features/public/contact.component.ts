import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';

@Component({
  selector: 'sumi-contact',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './contact.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ContactComponent {
  private sanitizer = inject(DomSanitizer);

  readonly mapUrl: SafeResourceUrl = this.sanitizer.bypassSecurityTrustResourceUrl(
    'https://www.google.com/maps?q=8846+Sampaloc+St,+corner+Estrella+St,+Makati+City,+1203+Metro+Manila&output=embed'
  );

  name = '';
  email = '';
  message = '';
  error = signal<string | null>(null);
  submitted = signal(false);
  submitting = signal(false);

  submit(event: Event): void {
    event.preventDefault();
    if (this.submitting()) return;

    const missing: string[] = [];
    if (!this.name.trim()) missing.push('name');
    if (!this.email.trim()) missing.push('email');
    if (!this.message.trim()) missing.push('message');
    if (missing.length > 0) {
      this.error.set('Please complete: ' + missing.join(', ') + '.');
      return;
    }

    const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailPattern.test(this.email.trim())) {
      this.error.set('Please enter a valid email address.');
      return;
    }

    this.submitting.set(true);
    this.error.set(null);
    setTimeout(() => {
      this.submitting.set(false);
      this.submitted.set(true);
      this.name = '';
      this.email = '';
      this.message = '';
    }, 400);
  }

  sendAnother(): void {
    this.submitted.set(false);
    this.error.set(null);
  }
}
