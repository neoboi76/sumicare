/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DecimalPipe } from '@angular/common';
import { PaymentDetails, PaymentDetailsService } from './payment-details.service';

interface CardForm {
    cardNumber: string;
    expMonth: string;
    expYear: string;
    cvc: string;
    cardHolder: string;
    cardEmail: string;
}

@Component({
    selector: 'sumi-payment-details-modal',
    standalone: true,
    imports: [FormsModule, DecimalPipe],
    templateUrl: './payment-details-modal.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class PaymentDetailsModalComponent {
    private service = inject(PaymentDetailsService);
    config = this.service.config;

    isCard = computed(() => this.config()?.method !== 'GCASH');

    card = signal<CardForm>({ cardNumber: '', expMonth: '', expYear: '', cvc: '', cardHolder: '', cardEmail: '' });
    gcashName = signal('');
    gcashPhone = signal('');
    gcashEmail = signal('');
    error = signal<string | null>(null);

    setCard<K extends keyof CardForm>(key: K, value: CardForm[K]): void {
        this.card.update(c => ({ ...c, [key]: value }));
    }

    useTestCard(): void {
        this.card.set({
            cardNumber: '4120 0000 0000 0007',
            expMonth: '12',
            expYear: '2030',
            cvc: '123',
            cardHolder: 'Test Cardholder',
            cardEmail: 'test.card@example.com'
        });
        this.error.set(null);
    }

    useTestGcash(): void {
        this.gcashName.set('Test GCash User');
        this.gcashPhone.set('09171234567');
        this.gcashEmail.set('test.gcash@example.com');
        this.error.set(null);
    }

    cancel(): void {
        this.reset();
        this.service.respond(null);
    }

    submit(): void {
        if (this.isCard()) {
            const c = this.card();
            const number = c.cardNumber.replace(/\s+/g, '');
            if (number.length < 12) {
                this.error.set('Enter a valid card number.');
                return;
            }
            if (!c.expMonth || !c.expYear || !c.cvc) {
                this.error.set('Enter the expiry month, year, and CVC.');
                return;
            }
            const details: PaymentDetails = {
                cardNumber: number,
                expMonth: c.expMonth,
                expYear: c.expYear,
                cvc: c.cvc,
                cardHolder: c.cardHolder,
                cardEmail: c.cardEmail.trim() || undefined
            };
            this.reset();
            this.service.respond(details);
            return;
        }
        const name = this.gcashName().trim();
        const phone = this.gcashPhone().trim();
        const email = this.gcashEmail().trim();
        if (!name) {
            this.error.set('Enter the GCash account name.');
            return;
        }
        if (!/^(09\d{9}|\+639\d{9})$/.test(phone)) {
            this.error.set('Enter a valid GCash mobile number (e.g. 09171234567).');
            return;
        }
        if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) {
            this.error.set('Enter a valid email address (required by GCash).');
            return;
        }
        const details: PaymentDetails = {
            gcashName: name,
            gcashPhone: phone,
            gcashEmail: email
        };
        this.reset();
        this.service.respond(details);
    }

    private reset(): void {
        this.card.set({ cardNumber: '', expMonth: '', expYear: '', cvc: '', cardHolder: '', cardEmail: '' });
        this.gcashName.set('');
        this.gcashPhone.set('');
        this.gcashEmail.set('');
        this.error.set(null);
    }
}
