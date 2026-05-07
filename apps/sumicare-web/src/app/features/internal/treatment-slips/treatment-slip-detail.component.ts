import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { DecimalPipe } from '@angular/common';
import { QRCodeComponent } from 'angularx-qrcode';
import { environment } from '../../../../environments/environment';

interface TreatmentSlip {
  id: string;
  tsn: string;
  clientNickname: string;
  lockerNumber: string | null;
  requestedTherapistNickname: string | null;
  primaryTherapistNickname: string | null;
  secondaryTherapistNickname: string | null;
  serviceName: string;
  roomNumber: string | null;
  startTime: string | null;
  endTime: string | null;
  vip: boolean;
  pax: number | null;
  treatmentMinutes: number | null;
  jacuzziMinutes: number | null;
  massageMinutes: number | null;
  wineIncluded: boolean | null;
  orNumber: string | null;
  addOnOrNumber: string | null;
  othersAddOn: string | null;
  remarks: string | null;
  totalAmount: number | null;
  waiverAccepted: boolean;
  waiverAcceptedAt: string | null;
  signedAt: string | null;
  createdAt: string;
}

@Component({
  selector: 'sumi-treatment-slip-detail',
  standalone: true,
  imports: [RouterLink, QRCodeComponent, DecimalPipe],
  templateUrl: './treatment-slip-detail.component.html',
  styleUrls: ['./treatment-slip-detail.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class TreatmentSlipDetailComponent implements OnInit {
  private http = inject(HttpClient);
  private route = inject(ActivatedRoute);
  slip = signal<TreatmentSlip | null>(null);

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) return;
    this.http.get<TreatmentSlip>(`${environment.apiBaseUrl}/api/treatment-slips/${id}`).subscribe({
      next: (s) => this.slip.set(s)
    });
  }

  formatTime(iso: string | null): string {
    if (!iso) return '—';
    return new Date(iso).toLocaleString();
  }

  formatDate(iso: string | null): string {
    if (!iso) return '—';
    return new Date(iso).toLocaleDateString();
  }

  feedbackUrl(tsn: string): string {
    return `${window.location.origin}/feedback?session=${tsn}`;
  }

  print(): void {
    window.print();
  }
}
