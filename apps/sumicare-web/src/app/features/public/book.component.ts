import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

interface ServiceItem {
  id: number;
  name: string;
  durationMinutes: number;
}

@Component({
  selector: 'sumi-book',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './book.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class BookComponent implements OnInit {
  private http = inject(HttpClient);
  services = signal<ServiceItem[]>([]);
  status = signal<string | null>(null);

  clientNickname = '';
  serviceId = 0;
  reservationType = 'SOFT';
  scheduledAt = '';
  consent = false;

  ngOnInit(): void {
    this.http
      .get<ServiceItem[]>(`${environment.apiBaseUrl}/api/public/services/${environment.defaultOrganizationSlug}`)
      .subscribe({ next: (s) => this.services.set(s), error: () => this.services.set([]) });
  }

  submit(event: Event): void {
    event.preventDefault();
    if (!this.consent) return;
    const payload = {
      clientNickname: this.clientNickname,
      serviceId: Number(this.serviceId),
      reservationType: this.reservationType,
      scheduledAt: new Date(this.scheduledAt).toISOString()
    };
    this.http
      .post(`${environment.apiBaseUrl}/api/public/bookings/${environment.defaultOrganizationSlug}`, payload)
      .subscribe({
        next: () => this.status.set('Booking received. Expect a confirmation by email if you provided one.'),
        error: () => this.status.set('Booking failed. Please try again.')
      });
  }
}
