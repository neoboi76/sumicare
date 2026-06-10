import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

type Step = 'request' | 'code' | 'review' | 'done';

interface CancellationDetails {
    reference: string;
    clientNickname: string;
    scheduledAt: string;
    reservationType: string;
    summary: string;
    roomType: string | null;
    total: number | null;
    paid: boolean;
    remarks: string | null;
}

@Component({
    selector: 'sumi-cancel',
    standalone: true,
    imports: [FormsModule, DecimalPipe, DatePipe, RouterLink],
    templateUrl: './cancel.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class CancelComponent {
    private http = inject(HttpClient);

    step = signal<Step>('request');
    busy = signal(false);
    error = signal<string | null>(null);
    notice = signal<string | null>(null);
    details = signal<CancellationDetails | null>(null);

    reference = '';
    email = '';
    code = '';

    private base(): string {
        return `${environment.apiBaseUrl}/api/public/bookings/${environment.defaultOrganizationSlug}/cancel`;
    }

    requestCode(): void {
        if (!this.reference.trim() || !this.email.trim()) {
            this.error.set('Enter your booking reference and email.');
            return;
        }
        this.busy.set(true);
        this.error.set(null);
        this.http.post<{ message: string }>(`${this.base()}/request`, {
            reference: this.reference.trim(),
            email: this.email.trim()
        }).subscribe({
            next: (res) => {
                this.busy.set(false);
                this.notice.set(res.message);
                this.step.set('code');
            },
            error: (err) => {
                this.busy.set(false);
                this.error.set(this.messageFor(err));
            }
        });
    }

    verifyCode(): void {
        if (!this.code.trim()) {
            this.error.set('Enter the code we sent to your email.');
            return;
        }
        this.busy.set(true);
        this.error.set(null);
        this.http.post<CancellationDetails>(`${this.base()}/verify`, {
            reference: this.reference.trim(),
            email: this.email.trim(),
            code: this.code.trim()
        }).subscribe({
            next: (res) => {
                this.busy.set(false);
                this.details.set(res);
                this.step.set('review');
            },
            error: (err) => {
                this.busy.set(false);
                this.error.set(this.messageFor(err));
            }
        });
    }

    confirmCancel(): void {
        this.busy.set(true);
        this.error.set(null);
        this.http.post<{ message: string }>(`${this.base()}/confirm`, {
            reference: this.reference.trim(),
            email: this.email.trim(),
            code: this.code.trim()
        }).subscribe({
            next: () => {
                this.busy.set(false);
                this.step.set('done');
            },
            error: (err) => {
                this.busy.set(false);
                this.error.set(this.messageFor(err));
            }
        });
    }

    restart(): void {
        this.step.set('request');
        this.details.set(null);
        this.notice.set(null);
        this.error.set(null);
        this.reference = '';
        this.email = '';
        this.code = '';
    }

    private messageFor(err: unknown): string {
        if (typeof err === 'object' && err !== null) {
            const anyErr = err as { error?: { message?: string }, status?: number };
            if (anyErr.error?.message) return anyErr.error.message;
            if (anyErr.status === 0) return 'Could not reach the server. Please check your connection and try again.';
        }
        return 'Something went wrong. Please try again.';
    }
}
