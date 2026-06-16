/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { SortDirection } from '../../utils/compare-by';

@Component({
  selector: 'sumi-sort-icon',
  standalone: true,
  templateUrl: './sort-icon.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SortIconComponent {
  @Input() active = false;
  @Input() direction: SortDirection = 'asc';
}
