package org.acm.os.domain.order;

/**
 * Order lifecycle states as defined in the design doc §7.1.
 *
 * <p>Only {@link #PENDING_PAYMENT} is relevant to the create-order use case; the remaining states
 * are declared for completeness and will be exercised by subsequent use cases.
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
