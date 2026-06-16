/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { inject } from '@angular/core';
import { SumiRevealDirective } from '../../shared/directives/sumi-reveal.directive';

@Component({
    selector: 'sumi-public-visit',
    standalone: true,
    imports: [RouterLink, SumiRevealDirective],
    templateUrl: './visit.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class VisitComponent {
    private sanitizer = inject(DomSanitizer);
    readonly mapUrl: SafeResourceUrl = this.sanitizer.bypassSecurityTrustResourceUrl(
        'https://www.google.com/maps?q=8846+Sampaloc+St+corner+Estrella+St+Makati+City&output=embed'
    );
}
