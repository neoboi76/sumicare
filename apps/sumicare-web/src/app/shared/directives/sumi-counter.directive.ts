import { AfterViewInit, Directive, ElementRef, Input, OnDestroy, PLATFORM_ID, inject } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

@Directive({
    selector: '[sumiCounter]',
    standalone: true
})
export class SumiCounterDirective implements AfterViewInit, OnDestroy {
    private readonly host = inject(ElementRef<HTMLElement>);
    private readonly platformId = inject(PLATFORM_ID);
    private observer: IntersectionObserver | null = null;
    private played = false;

    @Input('sumiCounter') target: number | string = 0;
    @Input() durationMs = 1400;
    @Input() prefix = '';
    @Input() suffix = '';
    @Input() decimals = 0;

    ngAfterViewInit(): void {
        if (!isPlatformBrowser(this.platformId)) {
            this.host.nativeElement.textContent = this.format(Number(this.target));
            return;
        }
        if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
            this.host.nativeElement.textContent = this.format(Number(this.target));
            return;
        }
        this.host.nativeElement.textContent = this.format(0);
        this.observer = new IntersectionObserver((entries) => {
            for (const entry of entries) {
                if (entry.isIntersecting && !this.played) {
                    this.played = true;
                    this.run();
                    this.observer?.disconnect();
                }
            }
        }, { threshold: 0.4 });
        this.observer.observe(this.host.nativeElement);
    }

    ngOnDestroy(): void {
        this.observer?.disconnect();
    }

    private run(): void {
        const target = Number(this.target);
        const start = performance.now();
        const tick = (now: number) => {
            const elapsed = now - start;
            const t = Math.min(elapsed / this.durationMs, 1);
            const eased = 1 - Math.pow(1 - t, 3);
            this.host.nativeElement.textContent = this.format(target * eased);
            if (t < 1) requestAnimationFrame(tick);
            else this.host.nativeElement.textContent = this.format(target);
        };
        requestAnimationFrame(tick);
    }

    private format(value: number): string {
        const rounded = this.decimals > 0 ? value.toFixed(this.decimals) : Math.round(value).toString();
        return `${this.prefix}${rounded}${this.suffix}`;
    }
}
