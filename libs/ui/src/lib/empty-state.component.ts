import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'sumi-empty-state',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="text-center py-10 text-slate-500">
      <div class="text-base font-medium text-slate-700">{{ title }}</div>
      <div class="text-sm mt-1" *ngIf="message">{{ message }}</div>
    </div>
  `
})
export class EmptyStateComponent {
  @Input() title = 'Nothing here yet';
  @Input() message?: string;
}
