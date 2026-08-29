package org.acm.os.domain.order;

import org.acm.os.domain.shared.BusinessException;

/**
 * Thrown when an order's declared currency differs from a product snapshot's currency (design
 * §6.2: all items in one order must share the same currency).
 *
 * <p>Maps to HTTP 400 via {@code GlobalExceptionHandler} (code {@code INVALID_REQUEST}).
 */
public class CurrencyMismatchException extends BusinessException {
  public CurrencyMismatchException(String message) {
    super("INVALID_REQUEST", message);
  }
}
