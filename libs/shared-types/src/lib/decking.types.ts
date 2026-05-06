export type DeckingFlag = 'NONE' | 'REQUESTED' | 'SCRUB' | 'ORDINARY' | 'BACKUP';

export interface DeckingEntry {
  therapistId: string;
  position: number;
  flag: DeckingFlag;
  skipped: boolean;
}
