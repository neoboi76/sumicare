/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SumiRevealDirective } from '../../shared/directives/sumi-reveal.directive';

@Component({
    selector: 'sumi-public-terms',
    standalone: true,
    imports: [RouterLink, SumiRevealDirective],
    templateUrl: './terms.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class TermsComponent {}
