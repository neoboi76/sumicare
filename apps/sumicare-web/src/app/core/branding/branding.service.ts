import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

export interface OrganizationBranding {
    id: string;
    slug: string;
    displayName: string;
    logoUrl: string | null;
    primaryColor: string;
    secondaryColor: string;
    accentColor: string;
    theme: string;
    fontFamily: string | null;
    loginBackgroundUrl: string | null;
    faviconUrl: string | null;
    instagramUrl: string | null;
    contactPhone: string | null;
    contactEmail: string | null;
    footerNote: string | null;
}

@Injectable({ providedIn: 'root' })
export class BrandingService {
    private http = inject(HttpClient);
    readonly branding = signal<OrganizationBranding | null>(null);

    loadPublicBranding(slug: string = environment.defaultOrganizationSlug): void {
        this.http
            .get<OrganizationBranding>(`${environment.apiBaseUrl}/api/public/branding/${slug}`)
            .subscribe({
                next: (value) => this.applyTheme(value),
                error: () => undefined
            });
    }

    loadCurrentBranding(): void {
        this.http
            .get<OrganizationBranding>(`${environment.apiBaseUrl}/api/organization/branding`)
            .subscribe({
                next: (value) => this.applyTheme(value),
                error: () => undefined
            });
    }

    applyTheme(branding: OrganizationBranding): void {
        this.branding.set(branding);
        const root = document.documentElement;
        if (branding.primaryColor) root.style.setProperty('--sumi-primary', branding.primaryColor);
        if (branding.secondaryColor) root.style.setProperty('--sumi-secondary', branding.secondaryColor);
        if (branding.accentColor) root.style.setProperty('--sumi-accent', branding.accentColor);
        if (branding.fontFamily) {
            const family = branding.fontFamily.includes(',')
                ? branding.fontFamily
                : `'${branding.fontFamily}', 'IBM Plex Sans', 'Inter', sans-serif`;
            root.style.setProperty('--sumi-app-font-display', family);
            root.style.setProperty('--sumi-app-font-body', family);
        } else {
            root.style.removeProperty('--sumi-app-font-display');
            root.style.removeProperty('--sumi-app-font-body');
        }
        if (branding.loginBackgroundUrl) {
            root.style.setProperty('--sumi-login-bg', `url('${branding.loginBackgroundUrl}')`);
        } else {
            root.style.removeProperty('--sumi-login-bg');
        }
        this.applyFavicon(branding.faviconUrl);
    }

    private applyFavicon(faviconUrl: string | null): void {
        if (!faviconUrl) return;
        let link = document.querySelector<HTMLLinkElement>('link[rel="icon"]');
        if (!link) {
            link = document.createElement('link');
            link.rel = 'icon';
            document.head.appendChild(link);
        }
        link.href = faviconUrl;
    }
}
