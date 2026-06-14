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
      brokerURL: environment.wsUrl.startsWith('http')
        ? environment.wsUrl.replace('http', 'ws')
        : environment.wsUrl,
      connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
      reconnectDelay: 5000
    });
    this.rx.activate();
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
