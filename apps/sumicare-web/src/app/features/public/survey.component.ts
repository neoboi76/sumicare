/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { environment } from '../../../environments/environment';

interface TherapistSection {
  therapistId: string;
  nickname: string;
}

interface SurveyDetail {
  orderReference: string | null;
  clientNickname: string | null;
  completed: boolean;
  therapists: TherapistSection[];
}

@Component({
  selector: 'sumi-survey',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './survey.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SurveyComponent implements OnInit {
  private http = inject(HttpClient);
  private route = inject(ActivatedRoute);

  token = '';
  loading = signal(true);
  detail = signal<SurveyDetail | null>(null);
  submitted = signal(false);
  error = signal<string | null>(null);

  lasemaRating = signal(0);
  lasemaComment = '';
  therapistRatings: Record<string, number> = {};
  therapistComments: Record<string, string> = {};

  tipGiven = signal(false);
  tipAmount: number | null = null;
  tipTherapistId = '';

  ngOnInit(): void {
    this.token = this.route.snapshot.queryParamMap.get('token') || '';
    if (!this.token) {
      this.loading.set(false);
      this.error.set('This survey link is invalid.');
      return;
    }
    this.http.get<SurveyDetail>(`${environment.apiBaseUrl}/api/public/survey/${this.token}`).subscribe({
      next: (d) => {
        this.detail.set(d);
        if (d.completed) this.submitted.set(true);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('We could not find this survey. The link may have expired.');
      }
    });
  }

  setLasema(rating: number): void {
    this.lasemaRating.set(rating);
  }

  setTherapistRating(therapistId: string, rating: number): void {
    this.therapistRatings[therapistId] = rating;
  }

  setTipGiven(given: boolean): void {
    this.tipGiven.set(given);
    if (!given) {
      this.tipAmount = null;
      this.tipTherapistId = '';
    } else {
      const d = this.detail();
      if (d && d.therapists.length === 1) this.tipTherapistId = d.therapists[0].therapistId;
    }
  }

  submit(): void {
    const d = this.detail();
    if (!d) return;
    if (this.lasemaRating() < 1) {
      this.error.set('Please rate your overall experience.');
      return;
    }
    if (this.tipGiven()) {
      if (!this.tipAmount || this.tipAmount <= 0) {
        this.error.set('Enter the tip amount in pesos.');
        return;
      }
      if (!this.tipTherapistId) {
        this.error.set('Select which therapist received the tip.');
        return;
      }
    }
    const therapists = d.therapists
      .filter(t => (this.therapistRatings[t.therapistId] || 0) >= 1)
      .map(t => ({
        therapistId: t.therapistId,
        rating: this.therapistRatings[t.therapistId],
        comment: (this.therapistComments[t.therapistId] || '').trim() || null
      }));
    this.error.set(null);
    this.http.post(`${environment.apiBaseUrl}/api/public/survey/${this.token}`, {
      lasemaRating: this.lasemaRating(),
      lasemaComment: this.lasemaComment.trim() || null,
      therapists,
      tipGiven: this.tipGiven(),
      tipAmount: this.tipGiven() ? this.tipAmount : null,
      tipTherapistId: this.tipGiven() ? (this.tipTherapistId || null) : null
    }).subscribe({
      next: () => this.submitted.set(true),
      error: (err) => this.error.set(err?.error?.message || 'Could not submit your survey. Please try again.')
    });
  }
}
