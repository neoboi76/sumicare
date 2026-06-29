/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'sumi-contact',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './contact.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ContactComponent {
  private sanitizer = inject(DomSanitizer);
  private http = inject(HttpClient);

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

    const url = `${environment.apiBaseUrl}/api/public/contact/${environment.defaultOrganizationSlug}`;
    this.http.post(url, {
      name: this.name.trim(),
      email: this.email.trim(),
      message: this.message.trim()
    }).subscribe({
      next: () => {
        this.submitting.set(false);
        this.submitted.set(true);
        this.name = '';
        this.email = '';
        this.message = '';
      },
      error: (err) => {
        this.submitting.set(false);
        if (err?.status === 429) {
          this.error.set('Too many messages from this address. Please try again later.');
        } else if (err?.status === 400) {
          this.error.set(err?.error?.message || 'Please review the form and try again.');
        } else {
          this.error.set('Could not send message. Please try again or contact us directly.');
        }
      }
    });
  }

  sendAnother(): void {
    this.submitted.set(false);
    this.error.set(null);
  }
}
