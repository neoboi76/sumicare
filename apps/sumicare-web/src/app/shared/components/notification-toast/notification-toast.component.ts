/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { NotificationFeedService, NotificationEvent, NotificationKey } from '../../../core/notifications/notification-feed.service';

interface Toast {
    id: number;
    key: NotificationKey;
    label: string;
    summary: string;
}

@Component({
    selector: 'sumi-notification-toast',
    standalone: true,
    imports: [],
    templateUrl: './notification-toast.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class NotificationToastComponent implements OnInit, OnDestroy {
    private feed = inject(NotificationFeedService);
    private router = inject(Router);
    private subscription: Subscription | null = null;
    private nextId = 1;

    readonly toasts = signal<Toast[]>([]);

    private static readonly LABELS: Record<NotificationKey, string> = {
        bookings: 'Bookings',
        orders: 'Orders',
        messages: 'Messages',
        feedback: 'Feedback'
    };

    private static readonly ROUTES: Record<NotificationKey, string> = {
        bookings: '/sumicare/app/bookings',
        orders: '/sumicare/app/orders',
        messages: '/sumicare/app/messages',
        feedback: '/sumicare/app/admin/feedback'
    };

    ngOnInit(): void {
        this.subscription = this.feed.events$.subscribe((event) => this.push(event));
    }

    ngOnDestroy(): void {
        this.subscription?.unsubscribe();
    }

    push(event: NotificationEvent): void {
        const toast: Toast = {
            id: this.nextId++,
            key: event.key,
            label: NotificationToastComponent.LABELS[event.key],
            summary: event.summary || NotificationToastComponent.LABELS[event.key] + ' update'
        };
        const next = [...this.toasts(), toast];
        if (next.length > 5) next.shift();
        this.toasts.set(next);
        setTimeout(() => this.dismiss(toast.id), 5500);
    }

    dismiss(id: number): void {
        this.toasts.update((list) => list.filter((t) => t.id !== id));
    }

    open(toast: Toast): void {
        this.router.navigateByUrl(NotificationToastComponent.ROUTES[toast.key]);
        this.dismiss(toast.id);
    }
}
