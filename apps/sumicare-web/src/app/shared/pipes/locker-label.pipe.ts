/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
    name: 'lockerLabel',
    standalone: true
})
export class LockerLabelPipe implements PipeTransform {
    transform(locker: string | null | undefined, gender: string | null | undefined, applyPrefix: boolean = true): string {
        if (locker === null || locker === undefined || locker === '') {
            return '';
        }
        const trimmed = String(locker).trim();
        if (!applyPrefix) {
            const raw = trimmed.replace(/^[MF]/i, '');
            return raw;
        }
        const g = gender ? String(gender).trim().toUpperCase() : '';
        if (g !== 'M' && g !== 'F') {
            return trimmed;
        }
        if (trimmed.toUpperCase().startsWith(g)) {
            return trimmed;
        }
        return g + trimmed;
    }
}
