package org.acm.os.application.port.out;

import org.acm.os.domain.shared.BusinessException;

/**
 * Thrown when one or more requested SKUs are unknown or not saleable.
 *
 * <p>Maps to HTTP 404 via {@code GlobalExceptionHandler} (code {@code PRODUCT_NOT_AVAILABLE}).
 */
public class ProductNotFoundException extends BusinessException {
  public ProductNotFoundException(String message) {
    super("PRODUCT_NOT_AVAILABLE", message);
  }
}
