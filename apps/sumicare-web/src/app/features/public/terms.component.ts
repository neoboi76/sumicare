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
