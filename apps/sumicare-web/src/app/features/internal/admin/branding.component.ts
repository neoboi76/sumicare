import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { BrandingService, OrganizationBranding } from '../../../core/branding/branding.service';

@Component({
  selector: 'sumi-branding',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './branding.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class BrandingComponent implements OnInit {
  private http = inject(HttpClient);
  private brandingService = inject(BrandingService);
  form = signal<OrganizationBranding | null>(null);

  ngOnInit(): void {
    this.http.get<OrganizationBranding>(`${environment.apiBaseUrl}/api/organization/branding`).subscribe({
      next: (b) => this.form.set(b),
      error: () => this.form.set(null)
    });
  }

  save(event: Event): void {
    event.preventDefault();
    const value = this.form();
    if (!value) return;
    this.http.put<OrganizationBranding>(`${environment.apiBaseUrl}/api/organization/branding`, value).subscribe({
      next: (saved) => {
        this.form.set(saved);
        this.brandingService.applyTheme(saved);
      }
    });
  }
}
