import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ToastService } from './toast.service';

@Component({
  selector: 'sumi-toast-host',
  standalone: true,
  templateUrl: './toast-host.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ToastHostComponent {
  protected toastService = inject(ToastService);

  classFor(kind: string): string {
    switch (kind) {
      case 'success': return 'border-emerald-200 bg-emerald-50 text-emerald-800';
      case 'info': return 'border-slate-200 bg-white text-slate-800';
      default: return 'border-red-200 bg-red-50 text-red-800';
    }
  }
}
