/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'sumi-paymongo-authorize',
    standalone: true,
    templateUrl: './paymongo-authorize.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class PaymongoAuthorizeComponent implements OnInit {
    private route = inject(ActivatedRoute);

    intentId = signal('');
    amount = signal('');
    method = signal('');
    processing = signal(false);
    private returnUrl = '';

    ngOnInit(): void {
        const params = this.route.snapshot.queryParamMap;
        this.intentId.set(params.get('intent') || '');
        this.amount.set(params.get('amount') || '');
        this.method.set((params.get('method') || '').toUpperCase());
        this.returnUrl = params.get('return') || '';
    }

    methodLabel(): string {
        switch (this.method()) {
            case 'GCASH': return 'GCash';
            case 'CREDIT': return 'Credit card';
            case 'DEBIT': return 'Debit card';
            default: return 'Payment';
        }
    }

    authorize(): void {
        this.complete('succeeded');
    }

    cancel(): void {
        this.complete('cancelled');
    }

    private complete(status: string): void {
        if (this.processing()) return;
        this.processing.set(true);
        const target = this.appendStatus(this.returnUrl, status);
        window.location.href = target;
    }

    private appendStatus(url: string, status: string): string {
        if (!url) return '/sumicare/app/cashier';
        const separator = url.includes('?') ? '&' : '?';
        return `${url}${separator}status=${status}`;
    }
}
