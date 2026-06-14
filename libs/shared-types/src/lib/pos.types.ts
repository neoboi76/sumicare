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
