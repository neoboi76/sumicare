/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

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
