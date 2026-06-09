import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, computed } from '@angular/core';

@Component({
  selector: 'sumi-paginator',
  standalone: true,
  templateUrl: './paginator.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PaginatorComponent {
  @Input({ required: true }) totalElements = 0;
  @Input({ required: true }) pageSize = 10;
  @Input({ required: true }) currentPage = 0;

  @Output() pageChange = new EventEmitter<number>();

  totalPages = computed(() => Math.ceil(this.totalElements / Math.max(1, this.pageSize)));

  startIndex = computed(() => this.currentPage * this.pageSize);
  endIndex = computed(() => Math.min(this.startIndex() + this.pageSize, this.totalElements));

  pages = computed(() => {
    const total = this.totalPages();
    const current = this.currentPage;
    const result: number[] = [];

    let start = Math.max(0, current - 2);
    let end = Math.min(total - 1, current + 2);

    if (start === 0) {
      end = Math.min(total - 1, 4);
    } else if (end === total - 1) {
      start = Math.max(0, total - 5);
    }

    for (let i = start; i <= end; i++) {
      result.push(i);
    }

    return result;
  });

  onPrev() {
    if (this.currentPage > 0) {
      this.pageChange.emit(this.currentPage - 1);
    }
  }

  onNext() {
    if (this.currentPage < this.totalPages() - 1) {
      this.pageChange.emit(this.currentPage + 1);
    }
  }

  onPage(page: number) {
    if (this.currentPage !== page) {
      this.pageChange.emit(page);
    }
  }
}
