import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

interface Feedback {
  id: string;
  ratingStars: number;
  comment: string | null;
  submittedAt: string;
}

@Component({
  selector: 'sumi-public-feedback',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './feedback.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class FeedbackComponent implements OnInit {
  private http = inject(HttpClient);
  private route = inject(ActivatedRoute);

  rating = signal(0);
  comment = '';
  sessionRef = signal<string | null>(null);
  submitted = signal(false);
  recent = signal<Feedback[]>([]);
  starButtons = [1, 2, 3, 4, 5];

  ngOnInit(): void {
    const session = this.route.snapshot.queryParamMap.get('session');
    if (session) this.sessionRef.set(session);
    this.loadRecent();
  }

  loadRecent(): void {
    this.http.get<Feedback[]>(`${environment.apiBaseUrl}/api/public/feedback/${environment.defaultOrganizationSlug}`).subscribe({
      next: (f) => this.recent.set(f),
      error: () => this.recent.set([])
    });
  }

  submit(event: Event): void {
    event.preventDefault();
    if (this.rating() === 0) return;
    const payload = { ratingStars: this.rating(), comment: this.comment, sessionRef: this.sessionRef() };
    this.http.post(`${environment.apiBaseUrl}/api/public/feedback/${environment.defaultOrganizationSlug}`, payload).subscribe({
      next: () => {
        this.submitted.set(true);
        this.comment = '';
        this.rating.set(0);
        this.loadRecent();
      }
    });
  }

  stars(rating: number): string {
    return '★'.repeat(rating) + '☆'.repeat(Math.max(0, 5 - rating));
  }

  formatDate(iso: string): string {
    return new Date(iso).toLocaleDateString();
  }
}
