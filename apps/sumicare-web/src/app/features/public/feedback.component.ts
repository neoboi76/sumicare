import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

interface Feedback {
  id: string;
  ratingStars: number;
  comment: string | null;
  nickname: string | null;
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
  nickname = '';
  sessionRef = signal<string | null>(null);
  orNumber = signal<string | null>(null);
  submitted = signal(false);
  submitting = signal(false);
  loadingInitial = signal(true);
  recent = signal<Feedback[]>([]);
  starButtons = [1, 2, 3, 4, 5];

  ngOnInit(): void {
    const session = this.route.snapshot.queryParamMap.get('session');
    const slip = this.route.snapshot.queryParamMap.get('slip');
    const or = this.route.snapshot.queryParamMap.get('or');
    this.sessionRef.set(session || slip || or || null);
    this.orNumber.set(or);
    this.loadRecent();
  }

  loadRecent(): void {
    this.loadingInitial.set(true);
    this.http.get<Feedback[]>(`${environment.apiBaseUrl}/api/public/feedback/${environment.defaultOrganizationSlug}`).subscribe({
      next: (f) => { this.recent.set(f); this.loadingInitial.set(false); },
      error: () => { this.recent.set([]); this.loadingInitial.set(false); }
    });
  }

  submit(event?: Event): void {
    if (event) event.preventDefault();
    if (this.rating() === 0 || this.submitting()) return;
    this.submitting.set(true);
    const payload = {
      ratingStars: this.rating(),
      comment: this.comment || null,
      nickname: this.nickname?.trim() || null,
      orNumber: this.orNumber() || null
    };
    this.http.post(`${environment.apiBaseUrl}/api/public/feedback/${environment.defaultOrganizationSlug}`, payload).subscribe({
      next: () => {
        this.submitted.set(true);
        this.submitting.set(false);
        this.comment = '';
        this.nickname = '';
        this.rating.set(0);
        this.loadRecent();
      },
      error: () => this.submitting.set(false)
    });
  }

  stars(rating: number): string {
    return '★'.repeat(rating) + '☆'.repeat(Math.max(0, 5 - rating));
  }

  formatDate(iso: string): string {
    return new Date(iso).toLocaleDateString();
  }
}
