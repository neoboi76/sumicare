import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { environment } from '../../../../environments/environment';

interface Feedback {
  id: string;
  ratingStars: number;
  comment: string | null;
  orNumber: string | null;
  submittedAt: string;
}

@Component({
  selector: 'sumi-admin-feedback',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './feedback.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class FeedbackAdminComponent implements OnInit {
  private http = inject(HttpClient);
  feedback = signal<Feedback[]>([]);
  exportFrom = '';
  exportTo = '';
  exporting = signal(false);

  ngOnInit(): void {
    this.http.get<{ content: Feedback[] }>(`${environment.apiBaseUrl}/api/feedback`).subscribe({
      next: (page) => this.feedback.set(page.content ?? [])
    });
  }

  stars(rating: number): string {
    return '★'.repeat(rating) + '☆'.repeat(Math.max(0, 5 - rating));
  }

  formatDate(iso: string): string {
    return new Date(iso).toLocaleString();
  }

  exportCsv(): void {
    this.exporting.set(true);
    let url = `${environment.apiBaseUrl}/api/feedback/export.csv`;
    const params: string[] = [];
    if (this.exportFrom) params.push(`from=${this.exportFrom}`);
    if (this.exportTo) params.push(`to=${this.exportTo}`);
    if (params.length) url += '?' + params.join('&');

    this.http.get(url, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = 'feedback.csv';
        a.click();
        URL.revokeObjectURL(a.href);
        this.exporting.set(false);
      },
      error: () => this.exporting.set(false)
    });
  }
}
