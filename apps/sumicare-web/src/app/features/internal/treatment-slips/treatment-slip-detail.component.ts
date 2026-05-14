import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
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
  status: string;
}

@Component({
  selector: 'sumi-treatment-slip-detail',
  standalone: true,
  imports: [RouterLink, QRCodeComponent, DecimalPipe, FormsModule],
  templateUrl: './treatment-slip-detail.component.html',
  styleUrls: ['./treatment-slip-detail.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class TreatmentSlipDetailComponent implements OnInit {
  private http = inject(HttpClient);
  private route = inject(ActivatedRoute);
  slip = signal<TreatmentSlip | null>(null);
  editing = signal(false);
  saveError = signal<string | null>(null);

  edit = {
    lockerNumber: '',
    roomNumber: '',
    othersAddOn: '',
    remarks: '',
    orNumber: '',
    addOnOrNumber: '',
    totalAmount: 0,
    jacuzziMinutes: 0,
    massageMinutes: 0,
    wineIncluded: false
  };

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) return;
    this.load(id);
  }

  private load(id: string): void {
    this.http.get<TreatmentSlip>(`${environment.apiBaseUrl}/api/treatment-slips/${id}`).subscribe({
      next: (s) => {
        this.slip.set(s);
        this.edit = {
          lockerNumber: s.lockerNumber || '',
          roomNumber: s.roomNumber || '',
          othersAddOn: s.othersAddOn || '',
          remarks: s.remarks || '',
          orNumber: s.orNumber || '',
          addOnOrNumber: s.addOnOrNumber || '',
          totalAmount: s.totalAmount || 0,
          jacuzziMinutes: s.jacuzziMinutes || 0,
          massageMinutes: s.massageMinutes || 0,
          wineIncluded: s.wineIncluded === true
        };
      }
    });
  }

  startEdit(): void {
    this.saveError.set(null);
    this.editing.set(true);
  }

  cancelEdit(): void {
    this.editing.set(false);
    this.saveError.set(null);
  }

  saveEdit(): void {
    const s = this.slip();
    if (!s) return;
    this.http.patch<TreatmentSlip>(`${environment.apiBaseUrl}/api/treatment-slips/${s.id}`, {
      lockerNumber: this.edit.lockerNumber || null,
      roomNumber: this.edit.roomNumber || null,
      othersAddOn: this.edit.othersAddOn || null,
      remarks: this.edit.remarks || null,
      orNumber: this.edit.orNumber || null,
      addOnOrNumber: this.edit.addOnOrNumber || null,
      totalAmount: Number(this.edit.totalAmount || 0),
      jacuzziMinutes: s.vip ? Number(this.edit.jacuzziMinutes || 0) : null,
      massageMinutes: s.vip ? Number(this.edit.massageMinutes || 0) : null,
      wineIncluded: s.vip ? this.edit.wineIncluded : null
    }).subscribe({
      next: (updated) => {
        this.slip.set(updated);
        this.editing.set(false);
      },
      error: (err) => this.saveError.set(err?.error?.message || 'Could not save changes.')
    });
  }

  formatTime(iso: string | null): string {
    if (!iso) return '—';
    return new Date(iso).toLocaleString();
  }

  formatTimeOnly(iso: string | null): string {
    if (!iso) return '';
    return new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }

  formatDate(iso: string | null): string {
    if (!iso) return '—';
    return new Date(iso).toLocaleDateString();
  }

  feedbackUrl(tsn: string): string {
    return `${window.location.origin}/feedback?session=${tsn}`;
  }

  print(): void {
    const original = document.title;
    document.title = ' ';
    const restore = () => {
      document.title = original;
      window.removeEventListener('afterprint', restore);
    };
    window.addEventListener('afterprint', restore);
    setTimeout(() => window.print(), 0);
  }
}
