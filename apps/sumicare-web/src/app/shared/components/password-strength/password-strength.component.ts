import { ChangeDetectionStrategy, Component, Input, computed, signal } from '@angular/core';

@Component({
  selector: 'sumi-password-strength',
  standalone: true,
  templateUrl: './password-strength.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PasswordStrengthComponent {
  private readonly pwd = signal('');

  @Input() set password(value: string | null | undefined) {
    this.pwd.set(value || '');
  }
  @Input() set value(value: string | null | undefined) {
    this.pwd.set(value || '');
  }

  hasLength = computed(() => this.pwd().length >= 8);
  hasUpper = computed(() => /[A-Z]/.test(this.pwd()));
  hasLower = computed(() => /[a-z]/.test(this.pwd()));
  hasDigit = computed(() => /[0-9]/.test(this.pwd()));
  hasSpecial = computed(() => /[^A-Za-z0-9]/.test(this.pwd()));

  score = computed(() => {
    let s = 0;
    if (this.hasLength()) s++;
    if (this.hasUpper() && this.hasLower()) s++;
    if (this.hasDigit()) s++;
    if (this.hasSpecial()) s++;
    return s;
  });

  strengthLabel = computed(() => {
    if (!this.pwd()) return 'Password strength';
    const s = this.score();
    if (s <= 1) return 'Weak';
    if (s === 2) return 'Fair';
    if (s === 3) return 'Good';
    return 'Strong';
  });

  textClass = computed(() => {
    if (!this.pwd()) return 'text-slate-500';
    const s = this.score();
    if (s <= 1) return 'text-rose-500';
    if (s === 2) return 'text-amber-500';
    if (s === 3) return 'text-emerald-500';
    return 'text-emerald-600';
  });

  bar1Class = computed(() => {
    if (!this.pwd()) return 'w-0';
    const s = this.score();
    if (s <= 1) return 'w-full bg-rose-500';
    if (s === 2) return 'w-full bg-amber-500';
    return 'w-full bg-emerald-500';
  });

  bar2Class = computed(() => {
    const s = this.score();
    if (s < 2) return 'w-0';
    if (s === 2) return 'w-full bg-amber-500';
    return 'w-full bg-emerald-500';
  });

  bar3Class = computed(() => {
    const s = this.score();
    if (s < 3) return 'w-0';
    return 'w-full bg-emerald-500';
  });

  bar4Class = computed(() => {
    const s = this.score();
    if (s < 4) return 'w-0';
    return 'w-full bg-emerald-600';
  });
}
