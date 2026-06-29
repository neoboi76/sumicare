/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

import { AfterViewInit, Directive, ElementRef, OnDestroy, PLATFORM_ID, inject } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

@Directive({
    selector: '[sumiReveal]',
    standalone: true
})
export class SumiRevealDirective implements AfterViewInit, OnDestroy {
    private readonly host = inject(ElementRef<HTMLElement>);
    private readonly platformId = inject(PLATFORM_ID);
    private observer: IntersectionObserver | null = null;

    ngAfterViewInit(): void {
        if (!isPlatformBrowser(this.platformId)) {
            this.host.nativeElement.classList.add('is-revealed');
            return;
        }
        const el = this.host.nativeElement;
        el.setAttribute('data-reveal', '');
        const reduced = typeof window !== 'undefined'
            && window.matchMedia
            && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        if (reduced) {
            el.classList.add('is-revealed');
            return;
        }
        this.observer = new IntersectionObserver((entries) => {
            for (const entry of entries) {
                if (entry.isIntersecting) {
                    entry.target.classList.add('is-revealed');
                    this.observer?.unobserve(entry.target);
                }
            }
        }, { rootMargin: '0px 0px -80px 0px', threshold: 0.08 });
        this.observer.observe(el);
    }

    ngOnDestroy(): void {
        this.observer?.disconnect();
        this.observer = null;
    }
}
