/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { inject } from '@angular/core';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'sumi-contact-admin-modal',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './contact-admin-modal.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ContactAdminModalComponent {
  private readonly http = inject(HttpClient);

  open = signal(false);
  submitting = signal(false);
  success = signal<string | null>(null);
  error = signal<string | null>(null);

  name = '';
  email = '';
  message = '';

  @Output() closed = new EventEmitter<void>();

  @Input() set show(value: boolean) {
    this.open.set(value);
    if (value) {
      this.success.set(null);
      this.error.set(null);
    }
  }

  canSubmit(): boolean {
    return this.name.trim().length > 0 && this.email.trim().length > 0;
  }

  submit(): void {
    if (this.submitting() || !this.canSubmit()) return;
    this.submitting.set(true);
    this.error.set(null);
    this.success.set(null);
    this.http.post<{ message: string }>(
      `${environment.apiBaseUrl}/api/auth/contact-admin-reset`,
      { name: this.name.trim(), email: this.email.trim(), message: this.message.trim() }
    ).subscribe({
      next: (res) => {
        this.success.set(res.message);
        this.name = '';
        this.message = '';
        this.submitting.set(false);
      },
      error: (err) => {
        this.error.set(err?.error?.message || 'Could not send your request. Please try again.');
        this.submitting.set(false);
      }
    });
  }

  close(): void {
    this.open.set(false);
    this.closed.emit();
  }
}
