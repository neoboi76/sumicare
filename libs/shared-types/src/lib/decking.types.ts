/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

export type DeckingFlag = 'NONE' | 'REQUESTED' | 'SCRUB' | 'ORDINARY' | 'BACKUP';

export interface DeckingEntry {
  therapistId: string;
  position: number;
  flag: DeckingFlag;
  skipped: boolean;
}
