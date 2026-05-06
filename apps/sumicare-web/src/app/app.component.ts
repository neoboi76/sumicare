import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { BrandingService } from './core/branding/branding.service';

@Component({
  selector: 'sumi-root',
  standalone: true,
  imports: [RouterOutlet],
  templateUrl: './app.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AppComponent implements OnInit {
  private branding = inject(BrandingService);

  ngOnInit(): void {
    this.branding.loadPublicBranding();
  }
}
