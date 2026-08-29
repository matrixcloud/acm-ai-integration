package org.acm.os.domain.order;

import org.acm.os.domain.shared.BusinessException;

public class OrderNotRefundableException extends BusinessException {
  public OrderNotRefundableException(String message) {
    super("ORDER_NOT_REFUNDABLE", message);
  }
}
