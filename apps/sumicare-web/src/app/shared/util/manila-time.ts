/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

const MANILA = 'Asia/Manila';

export function manilaToday(): string {
    return new Intl.DateTimeFormat('en-CA', {
        timeZone: MANILA,
        year: 'numeric',
        month: '2-digit',
        day: '2-digit'
    }).format(new Date());
}

export function manilaNowTime(): string {
    return new Intl.DateTimeFormat('en-GB', {
        timeZone: MANILA,
        hour: '2-digit',
        minute: '2-digit',
        hour12: false
    }).format(new Date());
}

export function manilaNowMinutes(): number {
    const [hours, minutes] = manilaNowTime().split(':').map(Number);
    return hours * 60 + minutes;
}

export function toManilaIso(date: string, time: string): string | null {
    if (!date || !time) {
        return null;
    }
    return `${date}T${time}:00+08:00`;
}
