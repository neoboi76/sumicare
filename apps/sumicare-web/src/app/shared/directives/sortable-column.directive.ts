import { Directive, EventEmitter, HostListener, Input, Output } from '@angular/core';
import { SortDirection, SortState, toggleSort } from '../utils/compare-by';

@Directive({
  selector: '[sumiSortable]',
  standalone: true,
  host: {
    'class': 'cursor-pointer select-none',
    'role': 'button',
    'tabindex': '0'
  }
})
export class SortableColumnDirective {
  @Input('sumiSortable') key = '';
  @Input() sortState: SortState = { key: null, direction: 'asc' };
  @Output() sortStateChange = new EventEmitter<SortState>();

  @HostListener('click')
  onClick(): void {
    const next = toggleSort(this.sortState, this.key);
    this.sortState = next;
    this.sortStateChange.emit(next);
  }

  @HostListener('keydown.enter', ['$event'])
  @HostListener('keydown.space', ['$event'])
  onKey(event: Event): void {
    event.preventDefault();
    this.onClick();
  }

  get active(): boolean {
    return this.sortState.key === this.key;
  }

  get direction(): SortDirection {
    return this.sortState.direction;
  }
}
