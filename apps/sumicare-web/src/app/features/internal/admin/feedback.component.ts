import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

interface Feedback {
  id: string;
  ratingStars: number;
  comment: string | null;
  submittedAt: string;
}

@Component({
  selector: 'sumi-admin-feedback',
  standalone: true,
  templateUrl: './feedback.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class FeedbackAdminComponent implements OnInit {
  private http = inject(HttpClient);
  feedback = signal<Feedback[]>([]);

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
}
