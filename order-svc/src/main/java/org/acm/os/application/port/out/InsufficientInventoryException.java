package org.acm.os.application.port.out;

import org.acm.os.domain.shared.BusinessException;

/**
 * Thrown when stock is insufficient to fulfill an order.
 *
 * <p>Maps to HTTP 409 via {@code GlobalExceptionHandler} (code {@code INSUFFICIENT_INVENTORY}).
 */
public class InsufficientInventoryException extends BusinessException {
  public InsufficientInventoryException(String message) {
    super("INSUFFICIENT_INVENTORY", message);
  }
}
