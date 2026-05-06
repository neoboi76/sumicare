import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'sumi-badge',
  standalone: true,
  imports: [CommonModule],
  template: `
    <span [class]="'inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ' + tone()">
      <ng-content></ng-content>
    </span>
  `
})
export class BadgeComponent {
  @Input() variant: 'neutral' | 'primary' | 'success' | 'warn' | 'danger' = 'neutral';

  tone(): string {
    switch (this.variant) {
      case 'primary': return 'bg-emerald-100 text-emerald-800';
      case 'success': return 'bg-green-100 text-green-800';
      case 'warn': return 'bg-amber-100 text-amber-800';
      case 'danger': return 'bg-red-100 text-red-800';
      default: return 'bg-slate-100 text-slate-700';
    }
  }
}
