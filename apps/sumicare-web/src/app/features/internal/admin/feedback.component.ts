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

interface FeedbackEntry {
  id: string;
  feedbackType: string | null;
  therapistId: string | null;
  therapistNickname: string | null;
  ratingStars: number;
  npsScore: number | null;
  comment: string | null;
  criteria: Record<string, number> | null;
  staffResponse: string | null;
  submittedAt: string;
}

interface OrderFeedbackGroup {
  orderId: string | null;
  orderReference: string | null;
  firstSubmittedAt: string;
  hasSurvey: boolean;
  entries: FeedbackEntry[];
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

  groups = signal<OrderFeedbackGroup[]>([]);
  searchTerm = signal('');
  expandedOrderId = signal<string | null>(null);
  exportFrom = '';
  exportTo = '';
  exporting = signal(false);

  filteredGroups = computed(() => {
    const term = this.searchTerm().trim().toLowerCase();
    if (!term) return this.groups();
    return this.groups().filter(g =>
      [g.orderReference ?? '', g.firstSubmittedAt,
       ...(g.entries.map(e => e.comment ?? '')),
       ...(g.entries.map(e => e.therapistNickname ?? ''))
      ].join(' ').toLowerCase().includes(term)
    );
  });

  toggle(key: string): void {
    this.expandedOrderId.update(current => current === key ? null : key);
  }

  groupKey(g: OrderFeedbackGroup): string {
    return g.orderId ?? g.entries[0]?.id ?? '';
  }

  ngOnInit(): void {
    this.http.get<OrderFeedbackGroup[]>(`${environment.apiBaseUrl}/api/feedback/by-order`).subscribe({
      next: (groups) => this.groups.set(groups)
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

  parsedCriteria(criteria: Record<string, number> | null): CriterionScore[] {
    if (!criteria) return [];
    return Object.entries(criteria).map(([key, score]) => ({ label: this.humanize(key), score }));
  }

  private humanize(key: string): string {
    const spaced = key.replace(/([A-Z])/g, ' $1');
    return spaced.charAt(0).toUpperCase() + spaced.slice(1);
  }

  formatDate(iso: string): string {
    return new Date(iso).toLocaleString('en-US', { timeZone: 'Asia/Manila' });
  }

  exportXlsx(): void {
    this.exporting.set(true);
    let url = `${environment.apiBaseUrl}/api/feedback/export.xlsx`;
    const params: string[] = [];
    if (this.exportFrom) params.push(`from=${this.exportFrom}`);
    if (this.exportTo) params.push(`to=${this.exportTo}`);
    if (params.length) url += '?' + params.join('&');

    this.http.get(url, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = 'feedback.xlsx';
        a.click();
        URL.revokeObjectURL(a.href);
        this.exporting.set(false);
      },
      error: () => this.exporting.set(false)
    });
  }
}
