import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-pay',
  templateUrl: './pay.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink]
})
export class PayComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private http = inject(HttpClient);

  bookingId = signal('');
  loading = signal(true);
  error = signal('');
  clientSecret = signal('');

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('bookingId') ?? '';
    this.bookingId.set(id);
    this.http
      .post<{ clientSecret: string }>(`${environment.apiBaseUrl}/api/pos/payment-intent`, {
        bookingId: id,
        amount: 0,
        currency: 'PHP'
      })
      .subscribe({
        next: (res) => { this.clientSecret.set(res.clientSecret); this.loading.set(false); },
        error: () => { this.error.set('Unable to initialize payment. Please try again or pay at the desk.'); this.loading.set(false); }
      });
  }
}
