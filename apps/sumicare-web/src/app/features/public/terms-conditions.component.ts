/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TermsContentComponent } from './terms-content.component';
import { SumiRevealDirective } from '../../shared/directives/sumi-reveal.directive';

@Component({
    selector: 'sumi-public-terms-conditions',
    standalone: true,
    imports: [TermsContentComponent, SumiRevealDirective],
    templateUrl: './terms-conditions.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class TermsConditionsComponent {}
