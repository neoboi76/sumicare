import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

@Component({
  selector: 'app-pay-result',
  templateUrl: './pay-result.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink]
})
export class PayResultComponent {
  private router = inject(Router);
  readonly success = computed(() => this.router.url.includes('/success'));
}
