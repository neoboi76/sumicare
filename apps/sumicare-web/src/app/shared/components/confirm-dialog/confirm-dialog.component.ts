import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, HostListener, ViewChild, effect, inject } from '@angular/core';
import { ConfirmService } from './confirm.service';

@Component({
    selector: 'sumi-confirm-dialog',
    standalone: true,
    templateUrl: './confirm-dialog.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class ConfirmDialogComponent implements AfterViewInit {
    private service = inject(ConfirmService);
    config = this.service.config;

    @ViewChild('confirmBtn') confirmBtn?: ElementRef<HTMLButtonElement>;

    constructor() {
        effect(() => {
            if (this.config()) {
                queueMicrotask(() => this.confirmBtn?.nativeElement.focus());
            }
        });
    }

    ngAfterViewInit(): void {
        if (this.config()) {
            this.confirmBtn?.nativeElement.focus();
        }
    }

    @HostListener('document:keydown.enter', ['$event'])
    onEnter(event: Event): void {
        if (!this.config()) return;
        const target = event.target as HTMLElement | null;
        if (target && (target.tagName === 'TEXTAREA' || target.isContentEditable)) return;
        event.preventDefault();
        this.confirm();
    }

    @HostListener('document:keydown.escape')
    onEscape(): void {
        if (!this.config()) return;
        this.cancel();
    }

    cancel() {
        this.service.respond(false);
    }

    confirm() {
        this.service.respond(true);
    }
}
