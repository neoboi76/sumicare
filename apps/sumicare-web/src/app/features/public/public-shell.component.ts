import { ChangeDetectionStrategy, Component, HostListener, inject, signal } from '@angular/core';
import { NgClass } from '@angular/common';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs/operators';
import { BrandingService } from '../../core/branding/branding.service';
import { routeFade } from '../../shared/animations/route-fade';

@Component({
    selector: 'sumi-public-shell',
    standalone: true,
    imports: [RouterOutlet, RouterLink, RouterLinkActive, NgClass],
    templateUrl: './public-shell.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    animations: [routeFade]
})
export class PublicShellComponent {
    protected branding = inject(BrandingService);
    private router = inject(Router);

    readonly currentYear = new Date().getFullYear();
    readonly menuOpen = signal(false);
    readonly scrolled = signal(false);
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

    @HostListener('window:scroll')
    onScroll(): void {
        const y = typeof window !== 'undefined' ? window.scrollY : 0;
        this.scrolled.set(y > 32);
    }

    toggleMenu(): void {
        this.menuOpen.update(v => !v);
    }

    closeMenu(): void {
        this.menuOpen.set(false);
    }
}
