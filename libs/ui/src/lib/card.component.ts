import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'sumi-card',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section class="bg-white rounded-xl shadow p-5">
      <header class="flex items-baseline justify-between mb-3" *ngIf="title">
        <h3 class="font-semibold text-slate-900">{{ title }}</h3>
        <span class="text-xs text-slate-500" *ngIf="subtitle">{{ subtitle }}</span>
      </header>
      <ng-content></ng-content>
    </section>
  `
})
export class CardComponent {
  @Input() title?: string;
  @Input() subtitle?: string;
}
