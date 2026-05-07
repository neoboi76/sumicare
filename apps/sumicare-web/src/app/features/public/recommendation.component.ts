import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

interface Service {
  id: number;
  code: string;
  name: string;
  durationMinutes: number;
  price: number;
}

interface RecommendationResponse {
  primary: Service | null;
  alternatives: Service[];
  rationale: string | null;
  aiUsed: boolean;
  disclaimer: string;
}

@Component({
  selector: 'sumi-recommendation',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './recommendation.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RecommendationComponent {
  private http = inject(HttpClient);
  questions = [
    { code: 'PRESSURE', label: 'Pressure preference', options: [
      { code: 'LIGHT', label: 'Light' }, { code: 'MEDIUM', label: 'Medium' },
      { code: 'FIRM', label: 'Firm' }, { code: 'VERY_FIRM', label: 'Very firm' }
    ]},
    { code: 'FOCUS_AREA', label: 'Focus area', options: [
      { code: 'FULL_BODY', label: 'Full body' }, { code: 'BACK', label: 'Back' },
      { code: 'FEET', label: 'Feet' }, { code: 'HEAD', label: 'Head' }
    ]},
    { code: 'TEXTURE', label: 'Dry or oil', options: [
      { code: 'DRY', label: 'Dry' }, { code: 'OIL', label: 'Oil' }
    ]},
    { code: 'DURATION', label: 'How long do you have', options: [
      { code: 'HALF', label: '30 min' }, { code: 'ONE', label: '1 hour' },
      { code: 'NINTY', label: '1.5 hours' }, { code: 'TWO', label: '2 hours' }
    ]},
    { code: 'GOAL', label: 'Primary goal', options: [
      { code: 'RELAX', label: 'Relaxation' }, { code: 'TENSION', label: 'Tension relief' },
      { code: 'FATIGUE', label: 'Fatigue recovery' }, { code: 'CIRCULATION', label: 'Circulation' }
    ]}
  ];

  answers = signal<Record<string, string>>({});
  result = signal<RecommendationResponse | null>(null);
  submittedOnce = signal(false);
  submitting = signal(false);

  allAnswered = computed(() =>
    this.questions.every(q => this.answers()[q.code] != null)
  );

  setAnswer(code: string, value: string): void {
    this.answers.update(prev => ({ ...prev, [code]: value }));
  }

  getAnswer(code: string): string {
    return this.answers()[code] ?? '';
  }

  submit(event: Event): void {
    event.preventDefault();
    this.submittedOnce.set(true);
    if (!this.allAnswered() || this.submitting()) return;
    this.submitting.set(true);
    const payload = {
      answers: Object.entries(this.answers()).map(([questionCode, optionCode]) => ({ questionCode, optionCode }))
    };
    this.http
      .post<RecommendationResponse>(`${environment.apiBaseUrl}/api/public/recommendation/${environment.defaultOrganizationSlug}`, payload)
      .subscribe({
        next: (r) => {
          this.result.set(r);
          this.submitting.set(false);
        },
        error: () => this.submitting.set(false)
      });
  }
}
