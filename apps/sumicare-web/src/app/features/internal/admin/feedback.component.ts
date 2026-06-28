/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { environment } from '../../../../environments/environment';
import { NotificationFeedService } from '../../../core/notifications/notification-feed.service';

interface Feedback {
  id: string;
  ratingStars: number;
  comment: string | null;
  orNumber: string | null;
  nickname: string | null;
  sessionId: string | null;
  submittedAt: string;
  feedbackType: string | null;
  therapistId: string | null;
  criteria: string | null;
  staffResponse: string | null;
}

interface CriterionScore {
  label: string;
  score: number;
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
  private feed = inject(NotificationFeedService);
  feedback = signal<Feedback[]>([]);
  searchTerm = signal('');
  expandedId = signal<string | null>(null);
  exportFrom = '';
  exportTo = '';
  exporting = signal(false);

  filteredFeedback = computed(() => {
    const term = this.searchTerm().trim().toLowerCase();
    if (!term) return this.feedback();
    return this.feedback().filter(f =>
      [f.orNumber ?? '', f.nickname ?? '', f.comment ?? ''].join(' ').toLowerCase().includes(term)
    );
  });

  toggle(id: string): void {
    this.expandedId.update(current => current === id ? null : id);
  }

  ngOnInit(): void {
    this.http.get<{ content: Feedback[] }>(`${environment.apiBaseUrl}/api/feedback`).subscribe({
      next: (page) => this.feedback.set(page.content ?? [])
    });
    this.http.post(`${environment.apiBaseUrl}/api/feedback/mark-all-read`, {}).subscribe({
      next: () => this.feed.markRead('feedback'),
      error: () => undefined
    });
  }

  stars(rating: number): string {
    return '★'.repeat(rating) + '☆'.repeat(Math.max(0, 5 - rating));
  }

  typeLabel(type: string | null): string {
    switch (type) {
      case 'LASEMA': return 'Lasema survey';
      case 'THERAPIST': return 'Therapist survey';
      default: return 'General feedback';
    }
  }

  isSurvey(type: string | null): boolean {
    return type === 'LASEMA' || type === 'THERAPIST';
  }

  parsedCriteria(criteria: string | null): CriterionScore[] {
    if (!criteria) return [];
    try {
      const map = JSON.parse(criteria) as Record<string, number>;
      return Object.entries(map).map(([key, score]) => ({ label: this.humanize(key), score }));
    } catch {
      return [];
    }
  }

  private humanize(key: string): string {
    const spaced = key.replace(/([A-Z])/g, ' $1');
    return spaced.charAt(0).toUpperCase() + spaced.slice(1);
  }

  formatDate(iso: string): string {
    return new Date(iso).toLocaleString('en-US', { timeZone: 'Asia/Manila' });
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
