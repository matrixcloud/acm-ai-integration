package org.acm.os.infra.client;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.acm.os.application.port.out.PaymentClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "order.adapters.payment", havingValue = "mock")
public class PaymentClientImpl implements PaymentClient {
  private final MockFailureRegistry failures;
  private final ConcurrentHashMap<String, PaymentSession> sessions = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, ExternalRefund> refunds = new ConcurrentHashMap<>();

  public PaymentClientImpl(MockFailureRegistry failures) {
    this.failures = failures;
  }

  @Override
  public PaymentSession create(
      String orderNo, BigDecimal amount, String currency, String idempotencyKey) {
    failures.check("payment-create");
    return sessions.computeIfAbsent(
        idempotencyKey, key -> new PaymentSession("mock-pay-" + UUID.randomUUID()));
  }

  @Override
  public ExternalRefund refund(
      String paymentNo, BigDecimal amount, String currency, String idempotencyKey) {
    failures.check("payment-refund");
    return refunds.computeIfAbsent(
        idempotencyKey, key -> new ExternalRefund("mock-refund-" + UUID.randomUUID()));
  }
}
