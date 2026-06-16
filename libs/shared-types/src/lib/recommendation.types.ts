/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

export interface QuizAnswer {
  questionCode: string;
  optionCode: string;
}

export interface QuizSubmissionRequest {
  clientId?: string;
  answers: QuizAnswer[];
}

export interface ServiceSummary {
  id: number;
  code: string;
  name: string;
  durationMinutes: number;
  price: number;
  category: string;
}

export interface RecommendationResponse {
  primary: ServiceSummary | null;
  alternatives: ServiceSummary[];
  rationale: string | null;
  aiUsed: boolean;
  disclaimer: string;
}
