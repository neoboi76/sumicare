import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { QRCodeComponent } from 'angularx-qrcode';
import { ActivatedRoute } from '@angular/router';
import { environment } from '../../../environments/environment';

interface ServiceItem {
  id: number;
  name: string;
  durationMinutes: number;
  price: number;
  fixedRate: boolean;
}

interface BookingCreated {
  id: string;
  clientNickname: string;
  reservationType: string;
  scheduledAt: string;
  effectiveStartAt: string;
  serviceId: number;
}

@Component({
  selector: 'sumi-book',
  standalone: true,
  imports: [FormsModule, QRCodeComponent],
  templateUrl: './book.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class BookComponent implements OnInit {
  private http = inject(HttpClient);
  private route = inject(ActivatedRoute);

  services = signal<ServiceItem[]>([]);
  error = signal<string | null>(null);
  submitting = signal(false);
  confirmation = signal<BookingCreated | null>(null);
  bookingRef = signal<string | null>(null);

  clientNickname = '';
  pax = 1;
  serviceId: number | null = null;
  reservationType = 'SOFT';
  scheduledDate = '';
  scheduledTime = '';
  clientGender = 'F';
  consent = false;

  serviceLabel = computed(() => {
    const id = this.confirmation()?.serviceId;
    if (id == null) return '';
    return this.services().find(s => s.id === id)?.name ?? '';
  });

  ngOnInit(): void {
    this.http
      .get<ServiceItem[]>(`${environment.apiBaseUrl}/api/public/services/${environment.defaultOrganizationSlug}`)
      .subscribe({
        next: (s) => {
          this.services.set(s);
          const queryId = Number(this.route.snapshot.queryParamMap.get('serviceId'));
          if (queryId && s.find(svc => svc.id === queryId)) {
            this.serviceId = queryId;
          } else if (s.length > 0 && this.serviceId === null) {
            this.serviceId = s[0].id;
          }
        },
        error: () => this.services.set([])
      });
  }

  submit(event: Event): void {
    event.preventDefault();
    if (this.submitting()) return;
    const missing: string[] = [];
    if (!this.clientNickname.trim()) missing.push('nickname');
    if (this.serviceId == null) missing.push('service');
    if (!this.scheduledDate) missing.push('date');
    if (!this.scheduledTime) missing.push('time');
    if (!this.consent) missing.push('consent');
    if (missing.length > 0) {
      this.error.set('Please complete: ' + missing.join(', ') + '.');
      return;
    }

    const combined = new Date(`${this.scheduledDate}T${this.scheduledTime}`);
    if (isNaN(combined.getTime())) {
      this.error.set('Please enter a valid date and time.');
      return;
    }

    this.submitting.set(true);
    this.error.set(null);

    const payload = {
      clientNickname: this.clientNickname.trim(),
      pax: Number(this.pax) || 1,
      serviceId: Number(this.serviceId),
      reservationType: this.reservationType,
      scheduledAt: combined.toISOString(),
      clientGender: this.clientGender
    };

    this.http
      .post<BookingCreated>(
        `${environment.apiBaseUrl}/api/public/bookings/${environment.defaultOrganizationSlug}`,
        payload
      )
      .subscribe({
        next: (booking) => {
          this.submitting.set(false);
          const ref = booking.id.slice(0, 8).toUpperCase();
          this.bookingRef.set(ref);
          this.confirmation.set(booking);
        },
        error: (err) => {
          this.submitting.set(false);
          this.error.set(this.extractErrorMessage(err));
        }
      });
  }

  bookAnother(): void {
    this.confirmation.set(null);
    this.bookingRef.set(null);
    this.error.set(null);
    this.clientNickname = '';
    this.pax = 1;
    this.scheduledDate = '';
    this.scheduledTime = '';
    this.clientGender = 'F';
    this.consent = false;
    if (this.services().length > 0) this.serviceId = this.services()[0].id;
  }

  formatDateTime(iso: string | null): string {
    if (!iso) return '';
    return new Date(iso).toLocaleString('en-PH', {
      dateStyle: 'long',
      timeStyle: 'short'
    });
  }

  private extractErrorMessage(err: unknown): string {
    if (typeof err === 'object' && err !== null) {
      const anyErr = err as { error?: { message?: string }, message?: string, status?: number };
      if (anyErr.error?.message) return anyErr.error.message;
      if (anyErr.status === 0) return 'Could not reach the booking server. Please check your connection and try again.';
      if (anyErr.status === 400) return 'One or more fields are invalid. Please review the form.';
      if (anyErr.message) return anyErr.message;
    }
    return 'Booking failed. Please try again or visit us in person.';
  }
}
