/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

import { Injectable } from '@angular/core';
import { RxStomp } from '@stomp/rx-stomp';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class StompService {
  private rx: RxStomp | null = null;

  connect(token: string | null): void {
    if (this.rx?.active) return;
    this.rx = new RxStomp();
    this.rx.configure({
      brokerURL: this.brokerUrl(),
      connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
      reconnectDelay: 5000
    });
    this.rx.activate();
  }

  private brokerUrl(): string {
    const path = environment.wsUrl || '/ws';
    const base = environment.apiBaseUrl;
    // When apiBaseUrl is absolute (production), derive the WS endpoint from it so the
    // socket targets the backend/proxy origin; http->ws and https->wss are preserved
    // by only rewriting the leading "http". Otherwise (local dev, relative apiBaseUrl)
    // fall back to the page origin so the socket stays same-origin.
    if (base && /^https?:\/\//i.test(base)) {
      return base.replace(/\/+$/, '').replace(/^http/i, 'ws') + path;
    }
    return window.location.origin.replace(/^http/i, 'ws') + path;
  }

  watch<T>(destination: string): Observable<T> {
    if (!this.rx) throw new Error('Stomp not connected');
    return this.rx.watch({ destination }).pipe(map((message) => JSON.parse(message.body) as T));
  }

  disconnect(): void {
    this.rx?.deactivate();
    this.rx = null;
  }
}
