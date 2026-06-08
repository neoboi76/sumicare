import { animate, style, transition, trigger } from '@angular/animations';

export const routeFade = trigger('routeFade', [
    transition('* <=> *', [
        style({ opacity: 0, transform: 'translateY(10px)' }),
        animate('320ms ease-out', style({ opacity: 1, transform: 'translateY(0)' }))
    ])
]);
