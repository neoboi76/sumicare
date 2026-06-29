/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

import { Injectable, signal } from '@angular/core';
import { Subject } from 'rxjs';

export type PaymentMethod = 'GCASH' | 'CREDIT' | 'DEBIT';

export interface PaymentDetails {
    cardNumber?: string;
    expMonth?: string;
    expYear?: string;
    cvc?: string;
    cardHolder?: string;
    cardEmail?: string;
    gcashName?: string;
    gcashPhone?: string;
    gcashEmail?: string;
}

export interface PaymentDetailsConfig {
    method: PaymentMethod;
    amount: number;
}

@Injectable({ providedIn: 'root' })
export class PaymentDetailsService {
    private configSignal = signal<PaymentDetailsConfig | null>(null);
    private responseSubject = new Subject<PaymentDetails | null>();

    get config() {
        return this.configSignal.asReadonly();
    }

    open(method: PaymentMethod, amount: number): Promise<PaymentDetails | null> {
        this.configSignal.set({ method, amount });
        return new Promise((resolve) => {
            const sub = this.responseSubject.subscribe((res) => {
                sub.unsubscribe();
                this.configSignal.set(null);
                resolve(res);
            });
        });
    }

    respond(result: PaymentDetails | null) {
        this.responseSubject.next(result);
    }
}
