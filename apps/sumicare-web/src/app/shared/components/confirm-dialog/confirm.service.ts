import { Injectable, signal } from '@angular/core';
import { Subject } from 'rxjs';

export interface ConfirmConfig {
  title: string;
  message: string;
  confirmText?: string;
  cancelText?: string;
  danger?: boolean;
}

@Injectable({ providedIn: 'root' })
export class ConfirmService {
  private configSignal = signal<ConfirmConfig | null>(null);
  private responseSubject = new Subject<boolean>();

  get config() {
    return this.configSignal.asReadonly();
  }

  confirm(config: ConfirmConfig): Promise<boolean> {
    this.configSignal.set(config);
    return new Promise((resolve) => {
      const sub = this.responseSubject.subscribe((res) => {
        sub.unsubscribe();
        this.configSignal.set(null);
        resolve(res);
      });
    });
  }

  respond(result: boolean) {
    this.responseSubject.next(result);
  }
}
