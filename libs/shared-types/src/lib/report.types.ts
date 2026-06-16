/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

export interface ReportSummary {
  from: string;
  to: string;
  sessionCount: number;
  commissionCount: number;
  commissionsByTherapist: Record<string, number>;
  sessionCountByTherapist: Record<string, number>;
  requestedCountByTherapist: Record<string, number>;
}
