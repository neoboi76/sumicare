import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs/operators';
import { BrandingService } from '../../core/branding/branding.service';

@Component({
  selector: 'sumi-public-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './public-shell.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PublicShellComponent {
  protected branding = inject(BrandingService);
  private router = inject(Router);

  readonly currentYear = new Date().getFullYear();
  readonly menuOpen = signal(false);

  constructor() {
    this.router.events
      .pipe(filter(e => e instanceof NavigationEnd))
      .subscribe(() => this.menuOpen.set(false));
  }

  toggleMenu(): void {
    this.menuOpen.update(v => !v);
  }

  closeMenu(): void {
    this.menuOpen.set(false);
  }
}
