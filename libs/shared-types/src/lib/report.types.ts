export interface ReportSummary {
  from: string;
  to: string;
  sessionCount: number;
  commissionCount: number;
  commissionsByTherapist: Record<string, number>;
  sessionCountByTherapist: Record<string, number>;
  requestedCountByTherapist: Record<string, number>;
}
