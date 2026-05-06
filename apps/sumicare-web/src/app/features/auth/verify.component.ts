import { Component, OnInit, signal, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-verify',
  templateUrl: './verify.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink]
})
export class VerifyComponent implements OnInit {
  readonly state = signal<'loading' | 'success' | 'expired' | 'invalid'>('loading');

  constructor(private route: ActivatedRoute, private http: HttpClient) {}

  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    const verified = params.get('verified');

    if (verified === '1') {
      this.state.set('success');
      return;
    }
    if (verified === 'expired') {
      this.state.set('expired');
      return;
    }
    if (verified === 'already') {
      this.state.set('success');
      return;
    }

    const token = params.get('token');
    if (!token) {
      this.state.set('invalid');
      return;
    }

    this.http
      .get(`${environment.apiBaseUrl}/api/auth/verify`, { params: { token }, observe: 'response' })
      .subscribe({
        next: () => this.state.set('success'),
        error: () => this.state.set('invalid')
      });
  }
}
