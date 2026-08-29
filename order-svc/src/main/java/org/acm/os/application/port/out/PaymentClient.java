package org.acm.os.application.port.out;

import java.math.BigDecimal;

public interface PaymentClient {
  PaymentSession create(
      String orderNo, BigDecimal amount, String currency, String idempotencyKey);

  ExternalRefund refund(
      String paymentNo, BigDecimal amount, String currency, String idempotencyKey);

  record PaymentSession(String paymentToken) {}

  record ExternalRefund(String externalRefundNo) {}
}
