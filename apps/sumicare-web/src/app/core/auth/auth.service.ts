import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, finalize, firstValueFrom, of, shareReplay, tap } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface AuthSession {
  accessToken: string;
  role: string;
  expiresAt: number;
}

interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  role: string;
}

export interface LoginResponse {
  mfaRequired: boolean;
  challengeId: string | null;
  email: string | null;
  token: TokenResponse | null;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  readonly session = signal<AuthSession | null>(null);
  private refreshInFlight: Observable<TokenResponse> | null = null;

  bootstrapSession(): Promise<void> {
    return firstValueFrom(
      this.http
        .post<TokenResponse>(`${environment.apiBaseUrl}/api/auth/refresh`, {}, { withCredentials: true })
        .pipe(
          tap((response) => this.applyToken(response)),
          catchError(() => of(null))
        )
    ).then(() => undefined);
  }

  login(username: string, password: string): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${environment.apiBaseUrl}/api/auth/login`, { username, password }, { withCredentials: true })
      .pipe(tap((response) => {
        if (response.token) {
          this.applyToken(response.token);
        }
      }));
  }

  verifyMfa(challengeId: string, code: string): Observable<TokenResponse> {
    return this.http
      .post<TokenResponse>(`${environment.apiBaseUrl}/api/auth/mfa/verify`, { challengeId, code }, { withCredentials: true })
      .pipe(tap((response) => this.applyToken(response)));
  }

  resendMfa(challengeId: string): Observable<void> {
    return this.http
      .post<void>(`${environment.apiBaseUrl}/api/auth/mfa/resend`, { challengeId }, { withCredentials: true });
  }

  refresh(): Observable<TokenResponse> {
    if (this.refreshInFlight) {
      return this.refreshInFlight;
    }
    this.refreshInFlight = this.http
      .post<TokenResponse>(`${environment.apiBaseUrl}/api/auth/refresh`, {}, { withCredentials: true })
      .pipe(
        tap((response) => this.applyToken(response)),
        finalize(() => { this.refreshInFlight = null; }),
        shareReplay({ bufferSize: 1, refCount: true })
      );
    return this.refreshInFlight;
  }

  logout(): Observable<void> {
    return this.http
      .post<void>(`${environment.apiBaseUrl}/api/auth/logout`, {}, { withCredentials: true })
      .pipe(tap(() => this.session.set(null)));
  }

  isAuthenticated(): boolean {
    const value = this.session();
    return value !== null && value.expiresAt > Date.now();
  }

  hasRole(roles: string[]): boolean {
    const value = this.session();
    return value !== null && roles.includes(value.role);
  }

  organizationId(): string | null {
    const token = this.session()?.accessToken;
    if (!token) return null;
    try {
      const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
      return payload.org ?? null;
    } catch {
      return null;
    }
  }

  private applyToken(response: TokenResponse): void {
    const expiresAt = Date.now() + response.expiresIn * 1000;
    this.session.set({ accessToken: response.accessToken, role: response.role, expiresAt });
  }
}
