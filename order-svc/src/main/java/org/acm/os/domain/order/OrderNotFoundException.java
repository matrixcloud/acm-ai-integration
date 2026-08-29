package org.acm.os.domain.order;

import org.acm.os.domain.shared.BusinessException;

public class OrderNotFoundException extends BusinessException {
  public OrderNotFoundException(String orderNo) {
    super("ORDER_NOT_FOUND", "Order '%s' does not exist".formatted(orderNo));
  }
}
