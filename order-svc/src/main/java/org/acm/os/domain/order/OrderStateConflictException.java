package org.acm.os.domain.order;

import org.acm.os.domain.shared.BusinessException;

public class OrderStateConflictException extends BusinessException {
  public OrderStateConflictException(String message) {
    super("ORDER_STATE_CONFLICT", message);
  }
}
