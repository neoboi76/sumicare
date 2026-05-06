import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

interface ServiceItem {
  id: number;
  code: string;
  name: string;
  durationMinutes: number;
  price: number;
  category: string;
  vip: boolean;
  fixedRate: boolean;
}

@Component({
  selector: 'sumi-services',
  standalone: true,
  imports: [DecimalPipe],
  templateUrl: './services.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ServicesComponent implements OnInit {
  private http = inject(HttpClient);
  services = signal<ServiceItem[]>([]);

  ngOnInit(): void {
    this.http
      .get<ServiceItem[]>(`${environment.apiBaseUrl}/api/public/services/${environment.defaultOrganizationSlug}`)
      .subscribe({
        next: (s) => this.services.set(s),
        error: () => this.services.set([])
      });
  }
}
