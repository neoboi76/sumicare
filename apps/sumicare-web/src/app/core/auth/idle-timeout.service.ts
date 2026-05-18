import { Injectable, NgZone, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';

const IDLE_MS = 15 * 60 * 1000;
const RESET_THROTTLE_MS = 30 * 1000;
const ACTIVITY_EVENTS = ['mousemove', 'keydown', 'click', 'scroll', 'touchstart'] as const;

@Injectable({ providedIn: 'root' })
export class IdleTimeoutService {
  private readonly zone = inject(NgZone);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  private timerHandle: ReturnType<typeof setTimeout> | null = null;
  private lastReset = 0;
  private boundHandler: ((event: Event) => void) | null = null;
  private active = false;

  start(): void {
    if (this.active) return;
    this.active = true;
    this.boundHandler = (event: Event) => this.onActivity(event);
    this.zone.runOutsideAngular(() => {
      for (const eventName of ACTIVITY_EVENTS) {
        document.addEventListener(eventName, this.boundHandler!, { passive: true, capture: true });
      }
      this.resetTimer();
    });
  }

  stop(): void {
    if (!this.active) return;
    this.active = false;
    if (this.boundHandler) {
      for (const eventName of ACTIVITY_EVENTS) {
        document.removeEventListener(eventName, this.boundHandler, { capture: true });
      }
      this.boundHandler = null;
    }
    this.clearTimer();
  }

  private onActivity(_event: Event): void {
    const now = Date.now();
    if (now - this.lastReset < RESET_THROTTLE_MS) return;
    this.lastReset = now;
    this.resetTimer();
  }

  private resetTimer(): void {
    this.clearTimer();
    this.timerHandle = setTimeout(() => this.expire(), IDLE_MS);
  }

  private clearTimer(): void {
    if (this.timerHandle !== null) {
      clearTimeout(this.timerHandle);
      this.timerHandle = null;
    }
  }

  private expire(): void {
    this.stop();
    this.zone.run(() => {
      this.auth.logout().subscribe({
        next: () => this.router.navigate(['/login'], { queryParams: { reason: 'idle' } }),
        error: () => this.router.navigate(['/login'], { queryParams: { reason: 'idle' } })
      });
    });
  }
}
