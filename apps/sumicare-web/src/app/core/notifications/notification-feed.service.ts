/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Subject, Subscription } from 'rxjs';
import { StompService } from '../realtime/stomp.service';
import { environment } from '../../../environments/environment';

export type NotificationKey = 'bookings' | 'orders' | 'messages' | 'feedback';

export interface NotificationEvent {
    key: NotificationKey;
    event: string;
    summary: string;
    at: string;
}

@Injectable({ providedIn: 'root' })
export class NotificationFeedService {
    private readonly stomp = inject(StompService);
    private readonly http = inject(HttpClient);
    private subscriptions: Subscription[] = [];
    private started = false;

    readonly unreadBookings = signal(0);
    readonly unreadOrders = signal(0);
    readonly unreadMessages = signal(0);
    readonly unreadFeedback = signal(0);
    readonly events$ = new Subject<NotificationEvent>();

    start(organizationId: string | null): void {
        if (this.started || !organizationId) return;
        this.started = true;
        this.seedUnreadCounts();
        this.subscriptions.push(this.watch('bookings', this.unreadBookings, organizationId));
        this.subscriptions.push(this.watch('orders', this.unreadOrders, organizationId));
        this.subscriptions.push(this.watch('messages', this.unreadMessages, organizationId));
        this.subscriptions.push(this.watch('feedback', this.unreadFeedback, organizationId));
    }

    // Seeds initial unread counts from persisted server state for the two streams
    // that survive a page reload (messages, feedback); bookings/orders start at zero
    // and accrue only from live events received this session.
    private seedUnreadCounts(): void {
        this.http.get<{ count: number }>(`${environment.apiBaseUrl}/api/contact-messages/unread-count`).subscribe({
            next: (r) => this.unreadMessages.set(r.count ?? 0),
            error: () => undefined
        });
        this.http.get<{ count: number }>(`${environment.apiBaseUrl}/api/feedback/unread-count`).subscribe({
            next: (r) => this.unreadFeedback.set(r.count ?? 0),
            error: () => undefined
        });
    }

    stop(): void {
        for (const sub of this.subscriptions) sub.unsubscribe();
        this.subscriptions = [];
        this.started = false;
    }

    markRead(key: NotificationKey): void {
        switch (key) {
            case 'bookings': this.unreadBookings.set(0); break;
            case 'orders': this.unreadOrders.set(0); break;
            case 'messages': this.unreadMessages.set(0); break;
            case 'feedback': this.unreadFeedback.set(0); break;
        }
    }

    unreadFor(key: NotificationKey): number {
        switch (key) {
            case 'bookings': return this.unreadBookings();
            case 'orders': return this.unreadOrders();
            case 'messages': return this.unreadMessages();
            case 'feedback': return this.unreadFeedback();
        }
    }

    private watch(key: NotificationKey, counter: { set(v: number): void; (): number }, organizationId: string): Subscription {
        try {
            return this.stomp.watch<{ event: string; summary: string; at: string }>('/topic/' + key + '/' + organizationId).subscribe({
                next: (message) => {
                    counter.set(counter() + 1);
                    this.events$.next({
                        key,
                        event: message.event ?? '',
                        summary: message.summary ?? '',
                        at: message.at ?? new Date().toISOString()
                    });
                },
                error: () => undefined
            });
        } catch {
            return new Subscription();
        }
    }
}
