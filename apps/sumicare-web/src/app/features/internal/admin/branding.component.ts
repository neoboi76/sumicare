/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { BrandingService, OrganizationBranding } from '../../../core/branding/branding.service';

interface FontOption {
    value: string;
    label: string;
    sample: string;
}

@Component({
    selector: 'sumi-branding',
    standalone: true,
    imports: [FormsModule, DatePipe],
    templateUrl: './branding.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class BrandingComponent implements OnInit {
    private http = inject(HttpClient);
    private brandingService = inject(BrandingService);
    form = signal<OrganizationBranding | null>(null);
    saving = signal(false);
    savedAt = signal<Date | null>(null);
    customFont = signal('');

    readonly fontOptions: FontOption[] = [
        { value: '', label: 'Default (IBM Plex Sans)', sample: 'IBM Plex Sans' },
        { value: 'Inter', label: 'Inter', sample: 'Inter' },
        { value: 'Source Sans 3', label: 'Source Sans 3', sample: 'Source Sans 3' },
        { value: 'Roboto', label: 'Roboto', sample: 'Roboto' },
        { value: 'Lato', label: 'Lato', sample: 'Lato' },
        { value: '__custom__', label: 'Custom', sample: '' }
    ];

    ngOnInit(): void {
        this.http.get<OrganizationBranding>(`${environment.apiBaseUrl}/api/organization/branding`).subscribe({
            next: (b) => {
                this.form.set(b);
                const known = this.fontOptions.find(o => o.value === (b.fontFamily ?? ''));
                if (!known && b.fontFamily) {
                    this.customFont.set(b.fontFamily);
                }
            },
            error: () => this.form.set(null)
        });
    }

    onFontFamilyChange(value: string): void {
        const current = this.form();
        if (!current) return;
        if (value === '__custom__') {
            this.form.set({ ...current, fontFamily: this.customFont() || null });
        } else {
            this.form.set({ ...current, fontFamily: value || null });
        }
        this.previewLocally();
    }

    onCustomFontInput(value: string): void {
        this.customFont.set(value);
        const current = this.form();
        if (!current) return;
        this.form.set({ ...current, fontFamily: value || null });
        this.previewLocally();
    }

    isCustomFont(): boolean {
        const value = this.form()?.fontFamily ?? '';
        return value !== '' && !this.fontOptions.some(o => o.value === value);
    }

    selectedFontValue(): string {
        const value = this.form()?.fontFamily ?? '';
        if (!value) return '';
        if (this.fontOptions.some(o => o.value === value)) return value;
        return '__custom__';
    }

    previewLocally(): void {
        const value = this.form();
        if (value) this.brandingService.applyTheme(value);
    }

    save(event: Event): void {
        event.preventDefault();
        const value = this.form();
        if (!value || this.saving()) return;
        this.saving.set(true);
        this.http.put<OrganizationBranding>(`${environment.apiBaseUrl}/api/organization/branding`, value).subscribe({
            next: (saved) => {
                this.form.set(saved);
                this.brandingService.applyTheme(saved);
                this.savedAt.set(new Date());
                this.saving.set(false);
            },
            error: () => this.saving.set(false)
        });
    }
}
