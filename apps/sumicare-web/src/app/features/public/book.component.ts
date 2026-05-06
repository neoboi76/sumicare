import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { QRCodeComponent } from 'angularx-qrcode';
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
  reservationType: string;
  shortRef?: string;
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

  services = signal<ServiceItem[]>([]);
  status = signal<string | null>(null);
  error = signal<string | null>(null);
  submitting = signal(false);
  bookingRef = signal<string | null>(null);

  clientNickname = '';
  pax = 1;
  serviceId = 0;
  reservationType = 'SOFT';
  scheduledAt = '';
  consent = false;

  ngOnInit(): void {
    this.http
      .get<ServiceItem[]>(`${environment.apiBaseUrl}/api/public/services/${environment.defaultOrganizationSlug}`)
      .subscribe({ next: (s) => { this.services.set(s); if (s.length > 0) this.serviceId = s[0].id; }, error: () => this.services.set([]) });
  }

  submit(event: Event): void {
    event.preventDefault();
    if (!this.consent || this.submitting()) return;
    this.submitting.set(true);
    this.error.set(null);
    const payload = {
      clientNickname: this.clientNickname,
      pax: Number(this.pax) || 1,
      serviceId: Number(this.serviceId),
      reservationType: this.reservationType,
      scheduledAt: new Date(this.scheduledAt).toISOString()
    };
    this.http
      .post<BookingCreated>(`${environment.apiBaseUrl}/api/public/bookings/${environment.defaultOrganizationSlug}`, payload)
      .subscribe({
        next: (booking) => {
          this.submitting.set(false);
          const ref = booking.id.slice(0, 8).toUpperCase();
          this.bookingRef.set(ref);
          this.status.set(`Reservation received. Reference: ${ref}. We will reach out to confirm your slot.`);
        },
        error: (err) => {
          this.submitting.set(false);
          this.error.set(err?.error?.message ?? 'Booking failed. Please try again or visit us in person.');
        }
      });
  }
}
