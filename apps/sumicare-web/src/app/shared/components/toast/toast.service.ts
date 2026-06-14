import { Injectable, signal } from '@angular/core';

export type ToastKind = 'error' | 'success' | 'info';

export interface Toast {
  id: number;
  kind: ToastKind;
  message: string;
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  private sequence = 0;
  readonly toasts = signal<Toast[]>([]);

  error(message: string): void {
    this.push('error', message);
  }

  success(message: string): void {
    this.push('success', message);
  }

  info(message: string): void {
    this.push('info', message);
  }

  dismiss(id: number): void {
    this.toasts.update(list => list.filter(t => t.id !== id));
  }

  private push(kind: ToastKind, message: string): void {
    const text = (message ?? '').trim();
    if (!text) return;
    const id = ++this.sequence;
    this.toasts.update(list => [...list, { id, kind, message: text }]);
    setTimeout(() => this.dismiss(id), 6000);
  }
}
