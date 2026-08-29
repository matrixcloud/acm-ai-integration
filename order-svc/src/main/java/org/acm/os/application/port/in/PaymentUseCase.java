package org.acm.os.application.port.in;

import org.acm.os.domain.order.Order;
import org.acm.os.domain.payment.Payment;

public interface PaymentUseCase {
  Payment createPayment(String orderNo, String idempotencyKey);

  Order succeedPayment(
      String paymentNo, String externalPaymentNo, String idempotencyKey);

  Order failPayment(String paymentNo, String idempotencyKey);
}
