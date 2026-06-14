import { Injectable, NgZone, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';

const IDLE_MS = 60 * 60 * 1000;
const RESET_THROTTLE_MS = 30 * 1000;
const REFRESH_INTERVAL_MS = 60 * 1000;
const REFRESH_WINDOW_MS = 3 * 60 * 1000;
const ACTIVITY_GRACE_MS = 2 * 60 * 1000;
const ACTIVITY_EVENTS = ['mousemove', 'keydown', 'click', 'scroll', 'touchstart'] as const;

@Injectable({ providedIn: 'root' })
export class IdleTimeoutService {
    private readonly zone = inject(NgZone);
    private readonly auth = inject(AuthService);
    private readonly router = inject(Router);

    private timerHandle: ReturnType<typeof setTimeout> | null = null;
    private refreshHandle: ReturnType<typeof setInterval> | null = null;
    private lastReset = 0;
    private lastActivity = 0;
    private refreshing = false;
    private boundHandler: ((event: Event) => void) | null = null;
    private active = false;

    start(): void {
        if (this.active) return;
        this.active = true;
        this.lastActivity = Date.now();
        this.boundHandler = (event: Event) => this.onActivity(event);
        this.zone.runOutsideAngular(() => {
            for (const eventName of ACTIVITY_EVENTS) {
                document.addEventListener(eventName, this.boundHandler!, { passive: true, capture: true });
            }
            this.resetTimer();
            this.startRefreshLoop();
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
        this.clearRefreshLoop();
    }

    private onActivity(_event: Event): void {
        this.lastActivity = Date.now();
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

    private startRefreshLoop(): void {
        this.refreshHandle = setInterval(() => this.maybeRefresh(), REFRESH_INTERVAL_MS);
    }

    private clearRefreshLoop(): void {
        if (this.refreshHandle !== null) {
            clearInterval(this.refreshHandle);
            this.refreshHandle = null;
        }
    }

    private maybeRefresh(): void {
        if (!this.active || this.refreshing) return;
        const session = this.auth.session();
        if (!session) return;
        const now = Date.now();
        if (now - this.lastActivity > ACTIVITY_GRACE_MS) return;
        const remaining = session.expiresAt - now;
        if (remaining > REFRESH_WINDOW_MS) return;
        this.refreshing = true;
        this.zone.run(() => {
            this.auth.refresh().subscribe({
                next: () => { this.refreshing = false; },
                error: () => { this.refreshing = false; }
            });
        });
    }

    private expire(): void {
        this.stop();
        this.zone.run(() => {
            this.auth.logout().subscribe({
                next: () => this.router.navigate(['/sumicare/login'], { queryParams: { reason: 'idle' } }),
                error: () => this.router.navigate(['/sumicare/login'], { queryParams: { reason: 'idle' } })
            });
        });
    }
}
