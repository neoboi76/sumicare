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
