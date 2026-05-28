import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SumiRevealDirective } from '../../shared/directives/sumi-reveal.directive';

@Component({
    selector: 'sumi-public-about',
    standalone: true,
    imports: [RouterLink, SumiRevealDirective],
    templateUrl: './about.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class AboutComponent {}
