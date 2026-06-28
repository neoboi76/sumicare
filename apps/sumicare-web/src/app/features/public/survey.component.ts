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

interface Criterion {
  key: string;
  label: string;
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

  readonly stars = [1, 2, 3, 4, 5];
  readonly lasemaCriteria: Criterion[] = [
    { key: 'cleanliness', label: 'Cleanliness' },
    { key: 'comfort', label: 'Comfort and ambiance' },
    { key: 'serviceQuality', label: 'Service quality' },
    { key: 'valueForMoney', label: 'Value for money' },
    { key: 'staffProfessionalism', label: 'Staff professionalism' }
  ];
  readonly therapistCriteria: Criterion[] = [
    { key: 'technique', label: 'Technique quality' },
    { key: 'communication', label: 'Communication' },
    { key: 'punctuality', label: 'Punctuality' },
    { key: 'professionalism', label: 'Professionalism' }
  ];

  lasemaOverall = signal(0);
  lasemaComment = '';
  lasemaScores: Record<string, number> = {};

  therapistOverall: Record<string, number> = {};
  therapistComments: Record<string, string> = {};
  therapistScores: Record<string, Record<string, number>> = {};
  tipAmounts: Record<string, number | null> = {};

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
        for (const t of d.therapists) {
          this.therapistScores[t.therapistId] = {};
        }
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('We could not find this survey. The link may have expired.');
      }
    });
  }

  setLasemaOverall(rating: number): void {
    this.lasemaOverall.set(rating);
  }

  setLasemaScore(key: string, rating: number): void {
    this.lasemaScores[key] = rating;
  }

  setTherapistOverall(therapistId: string, rating: number): void {
    this.therapistOverall[therapistId] = rating;
  }

  setTherapistScore(therapistId: string, key: string, rating: number): void {
    this.therapistScores[therapistId][key] = rating;
  }

  submit(): void {
    const d = this.detail();
    if (!d) return;
    if (this.lasemaOverall() < 1) {
      this.error.set('Please rate your overall Lasema experience.');
      return;
    }
    const therapists = d.therapists
      .filter(t => (this.therapistOverall[t.therapistId] || 0) >= 1)
      .map(t => ({
        therapistId: t.therapistId,
        rating: this.therapistOverall[t.therapistId],
        comment: (this.therapistComments[t.therapistId] || '').trim() || null,
        criteria: this.therapistScores[t.therapistId] || {}
      }));
    const tips = d.therapists
      .filter(t => (this.tipAmounts[t.therapistId] || 0) > 0)
      .map(t => ({ therapistId: t.therapistId, amount: this.tipAmounts[t.therapistId] }));

    this.error.set(null);
    this.http.post(`${environment.apiBaseUrl}/api/public/survey/${this.token}`, {
      lasemaRating: this.lasemaOverall(),
      lasemaComment: this.lasemaComment.trim() || null,
      lasemaCriteria: this.lasemaScores,
      therapists,
      tips
    }).subscribe({
      next: () => this.submitted.set(true),
      error: (err) => this.error.set(err?.error?.message || 'Could not submit your survey. Please try again.')
    });
  }
}
