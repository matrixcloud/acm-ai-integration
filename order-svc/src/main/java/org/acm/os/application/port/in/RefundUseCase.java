package org.acm.os.application.port.in;

import org.acm.os.domain.order.Order;
import org.acm.os.domain.refund.Refund;

public interface RefundUseCase {
  Order cancel(String orderNo, String reason, String idempotencyKey);

  Refund requestRefund(String orderNo, String reason, String idempotencyKey);

  Refund approveRefund(String refundNo, String reviewer, String comment, String idempotencyKey);

  Refund rejectRefund(String refundNo, String reviewer, String comment, String idempotencyKey);

  Refund retryRefund(String refundNo, String idempotencyKey);
}
