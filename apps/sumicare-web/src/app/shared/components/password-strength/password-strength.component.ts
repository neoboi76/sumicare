import { ChangeDetectionStrategy, Component, Input, computed } from '@angular/core';

@Component({
  selector: 'sumi-password-strength',
  standalone: true,
  template: `
    <div class="mt-2 space-y-2">
      <div class="flex gap-1 h-1.5 w-full bg-slate-100 rounded-full overflow-hidden">
        <div class="h-full transition-all duration-300" [class]="bar1Class()"></div>
        <div class="h-full transition-all duration-300" [class]="bar2Class()"></div>
        <div class="h-full transition-all duration-300" [class]="bar3Class()"></div>
        <div class="h-full transition-all duration-300" [class]="bar4Class()"></div>
      </div>
      <p class="text-[11px] font-medium" [class]="textClass()">{{ strengthLabel() }}</p>
      
      <div class="grid grid-cols-2 gap-1 text-[11px] text-slate-500 mt-1">
        <div class="flex items-center gap-1" [class.text-emerald-600]="hasLength()">
          <span class="w-3 text-center">{{ hasLength() ? '✓' : '○' }}</span> 8+ chars
        </div>
        <div class="flex items-center gap-1" [class.text-emerald-600]="hasUpper()">
          <span class="w-3 text-center">{{ hasUpper() ? '✓' : '○' }}</span> Uppercase
        </div>
        <div class="flex items-center gap-1" [class.text-emerald-600]="hasLower()">
          <span class="w-3 text-center">{{ hasLower() ? '✓' : '○' }}</span> Lowercase
        </div>
        <div class="flex items-center gap-1" [class.text-emerald-600]="hasDigit()">
          <span class="w-3 text-center">{{ hasDigit() ? '✓' : '○' }}</span> Number
        </div>
        <div class="flex items-center gap-1" [class.text-emerald-600]="hasSpecial()">
          <span class="w-3 text-center">{{ hasSpecial() ? '✓' : '○' }}</span> Special
        </div>
      </div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PasswordStrengthComponent {
  @Input() set password(value: string | null | undefined) {
    this.pwd = value || '';
  }

  private pwd = '';

  hasLength = computed(() => this.pwd.length >= 8);
  hasUpper = computed(() => /[A-Z]/.test(this.pwd));
  hasLower = computed(() => /[a-z]/.test(this.pwd));
  hasDigit = computed(() => /[0-9]/.test(this.pwd));
  hasSpecial = computed(() => /[^A-Za-z0-9]/.test(this.pwd));

  score = computed(() => {
    let s = 0;
    if (this.hasLength()) s++;
    if (this.hasUpper() && this.hasLower()) s++;
    if (this.hasDigit()) s++;
    if (this.hasSpecial()) s++;
    return s;
  });

  strengthLabel = computed(() => {
    if (!this.pwd) return 'Password strength';
    const s = this.score();
    if (s <= 1) return 'Weak';
    if (s === 2) return 'Fair';
    if (s === 3) return 'Good';
    return 'Strong';
  });

  textClass = computed(() => {
    if (!this.pwd) return 'text-slate-500';
    const s = this.score();
    if (s <= 1) return 'text-rose-500';
    if (s === 2) return 'text-amber-500';
    if (s === 3) return 'text-emerald-500';
    return 'text-emerald-600';
  });

  bar1Class = computed(() => {
    if (!this.pwd) return 'w-0';
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
