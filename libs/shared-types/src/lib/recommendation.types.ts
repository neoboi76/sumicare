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
