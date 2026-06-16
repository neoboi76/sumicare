/*
 * Developed by the following authors:
 *     Lance Gabriel C. De La Paz (lgcdelapaz@mymail.mapua.edu.ph)
 *     Franz C. Pereira (fcpereira@mymail.mapua.edu.ph)
 *     Dino Alfred T. Timbol (dattimbol@mymail.mapua.edu.ph)
 */

export type PaymentMethod = 'CASH' | 'GCASH' | 'CREDIT' | 'DEBIT';

export interface ProcessPaymentRequest {
  sessionId: string;
  subtotal: number;
  discount?: number;
  paymentMethod: PaymentMethod;
}

export interface PaymentResponse {
  transactionId: string;
  receiptNumber: string;
  subtotal: number;
  discount: number;
  total: number;
  paymentMethod: PaymentMethod;
  processedAt: string;
}
