import { Directive, EventEmitter, HostListener, Input, OnDestroy, Output } from '@angular/core';
import { Subject, Subscription } from 'rxjs';
import { debounceTime } from 'rxjs/operators';

@Directive({
  selector: '[debounceClick]',
  exportAs: 'debounceClick'
})
export class DebounceClickDirective implements OnDestroy {
  @Input() debounceTime = 500;
  @Output() debounceClick = new EventEmitter<MouseEvent>();

  private clicks = new Subject<MouseEvent>();
  private subscription: Subscription;
  private submitting = false;

  constructor() {
    this.subscription = this.clicks
      .pipe(debounceTime(this.debounceTime))
      .subscribe((event) => {
        if (!this.submitting) {
          this.submitting = true;
          this.debounceClick.emit(event);
          setTimeout(() => (this.submitting = false), this.debounceTime);
        }
      });
  }

  @HostListener('click', ['$event'])
  clickEvent(event: MouseEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.clicks.next(event);
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
