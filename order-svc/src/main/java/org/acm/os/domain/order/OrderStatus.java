package org.acm.os.domain.order;

/**
 * Order lifecycle states as defined in the design doc §7.1.
 *
 * <p>Transitions are owned by {@link Order}; application services must not assign states directly.
 */
public enum OrderStatus {
  PENDING_PAYMENT,
  PAID,
  REFUND_REVIEW,
  REFUNDING,
  REFUND_FAILED,
  REFUNDED,
  CANCELING,
  CANCEL_FAILED,
  CANCELED,
  PARTIALLY_SHIPPED,
  SHIPPED,
  COMPLETED
}
