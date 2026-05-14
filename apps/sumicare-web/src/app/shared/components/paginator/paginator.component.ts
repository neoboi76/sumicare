import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, computed } from '@angular/core';

@Component({
  selector: 'sumi-paginator',
  standalone: true,
  template: `
    <div class="flex items-center justify-between px-4 py-3 bg-white border-t border-slate-200 sm:px-6">
      <div class="flex justify-between flex-1 sm:hidden">
        <button [disabled]="currentPage === 0" (click)="onPrev()" 
                class="relative inline-flex items-center px-4 py-2 text-sm font-medium text-slate-700 bg-white border border-slate-300 rounded-md hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed">
          Previous
        </button>
        <button [disabled]="currentPage >= totalPages() - 1" (click)="onNext()" 
                class="relative inline-flex items-center px-4 py-2 ml-3 text-sm font-medium text-slate-700 bg-white border border-slate-300 rounded-md hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed">
          Next
        </button>
      </div>
      <div class="hidden sm:flex sm:flex-1 sm:items-center sm:justify-between">
        <div>
          <p class="text-sm text-slate-700">
            Showing
            <span class="font-medium">{{ startIndex() + 1 }}</span>
            to
            <span class="font-medium">{{ endIndex() }}</span>
            of
            <span class="font-medium">{{ totalElements }}</span>
            results
          </p>
        </div>
        <div>
          <nav class="inline-flex -space-x-px rounded-md shadow-sm" aria-label="Pagination">
            <button [disabled]="currentPage === 0" (click)="onPrev()"
                    class="relative inline-flex items-center px-2 py-2 text-slate-400 bg-white border border-slate-300 rounded-l-md hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed">
              <span class="sr-only">Previous</span>
              <svg class="w-5 h-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                <path fill-rule="evenodd" d="M12.79 5.23a.75.75 0 01-.02 1.06L8.832 10l3.938 3.71a.75.75 0 11-1.04 1.08l-4.5-4.25a.75.75 0 010-1.08l4.5-4.25a.75.75 0 011.06.02z" clip-rule="evenodd" />
              </svg>
            </button>
            
            @for (page of pages(); track page) {
              <button (click)="onPage(page)"
                      [class.z-10]="currentPage === page"
                      [class.bg-\[var\(--sumi-primary\)\]]="currentPage === page"
                      [class.text-white]="currentPage === page"
                      [class.text-slate-900]="currentPage !== page"
                      [class.hover:bg-slate-50]="currentPage !== page"
                      class="relative inline-flex items-center px-4 py-2 text-sm font-semibold border border-slate-300 focus:z-20">
                {{ page + 1 }}
              </button>
            }

            <button [disabled]="currentPage >= totalPages() - 1" (click)="onNext()"
                    class="relative inline-flex items-center px-2 py-2 text-slate-400 bg-white border border-slate-300 rounded-r-md hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed">
              <span class="sr-only">Next</span>
              <svg class="w-5 h-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                <path fill-rule="evenodd" d="M7.21 14.77a.75.75 0 01.02-1.06L11.168 10 7.23 6.29a.75.75 0 111.04-1.08l4.5 4.25a.75.75 0 010 1.08l-4.5 4.25a.75.75 0 01-1.06-.02z" clip-rule="evenodd" />
              </svg>
            </button>
          </nav>
        </div>
      </div>
    </div>
  `,
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
    
    // Show up to 5 page numbers (current - 2 to current + 2)
    let start = Math.max(0, current - 2);
    let end = Math.min(total - 1, current + 2);
    
    // Adjust if we're near the edges
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
