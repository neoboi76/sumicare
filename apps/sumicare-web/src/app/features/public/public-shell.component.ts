import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
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
}
