/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  OnInit,
  Output,
  signal
} from '@angular/core';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'sumi-password-input',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './password-input.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PasswordInputComponent {
  @Input() value = '';
  @Input() name = 'password';
  @Input() inputId = '';
  @Input() placeholder = '';
  @Input() autocomplete = 'current-password';
  @Input() required = false;
  @Input() disabled = false;
  @Input() maxlength: number | null = null;
  @Output() valueChange = new EventEmitter<string>();

  visible = signal(false);

  toggleVisibility(): void {
    this.visible.update(v => !v);
  }

  onChange(val: string): void {
    this.value = val;
    this.valueChange.emit(val);
  }
}
