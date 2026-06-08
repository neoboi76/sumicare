import { AfterViewInit, Directive, ElementRef, HostListener, Input, OnDestroy, PLATFORM_ID, inject } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

@Directive({
    selector: '[sumiParallax]',
    standalone: true
})
export class SumiParallaxDirective implements AfterViewInit, OnDestroy {
    private readonly host = inject(ElementRef<HTMLElement>);
    private readonly platformId = inject(PLATFORM_ID);
    private enabled = false;
    private ticking = false;
    private reduced = false;

    @Input('sumiParallax') speed: number | string = 0.35;

    ngAfterViewInit(): void {
        if (!isPlatformBrowser(this.platformId)) return;
        this.reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        this.enabled = window.innerWidth >= 768 && !this.reduced;
        if (this.enabled) this.update();
    }

    ngOnDestroy(): void {
        this.enabled = false;
    }

    @HostListener('window:scroll')
    onScroll(): void {
        if (!this.enabled || this.ticking) return;
        this.ticking = true;
        requestAnimationFrame(() => {
            this.update();
            this.ticking = false;
        });
    }

    @HostListener('window:resize')
    onResize(): void {
        this.enabled = window.innerWidth >= 768 && !this.reduced;
        this.update();
    }

    private update(): void {
        const el = this.host.nativeElement;
        const rect = el.getBoundingClientRect();
        const viewportHeight = window.innerHeight;
        const offsetCenter = rect.top + rect.height / 2 - viewportHeight / 2;
        const factor = typeof this.speed === 'string' ? parseFloat(this.speed) : this.speed;
        if (!this.enabled) {
            el.style.transform = '';
            return;
        }
        el.style.transform = `translate3d(0, ${(-offsetCenter * factor).toFixed(2)}px, 0)`;
        el.style.willChange = 'transform';
    }
}
