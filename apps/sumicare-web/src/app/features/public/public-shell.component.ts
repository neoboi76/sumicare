import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs/operators';
import { BrandingService } from '../../core/branding/branding.service';
import { routeFade } from '../../shared/animations/route-fade';

@Component({
    selector: 'sumi-public-shell',
    standalone: true,
    imports: [RouterOutlet, RouterLink, RouterLinkActive],
    templateUrl: './public-shell.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    animations: [routeFade]
})
export class PublicShellComponent {
    protected branding = inject(BrandingService);
    private router = inject(Router);

    readonly currentYear = new Date().getFullYear();
    readonly menuOpen = signal(false);
    readonly routeToken = signal(0);

    constructor() {
        this.router.events
            .pipe(filter(e => e instanceof NavigationEnd))
            .subscribe(() => {
                this.menuOpen.set(false);
                this.routeToken.update(v => v + 1);
                if (typeof window !== 'undefined') window.scrollTo({ top: 0, behavior: 'instant' as ScrollBehavior });
            });
    }

    toggleMenu(): void {
        this.menuOpen.update(v => !v);
    }

    closeMenu(): void {
        this.menuOpen.set(false);
    }
}
