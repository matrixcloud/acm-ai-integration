package org.acm.os.domain.order;

import org.acm.os.domain.shared.BusinessException;

/**
 * Thrown when an order contains two or more lines for the same SKU.
 *
 * <p>Maps to HTTP 409 via {@code GlobalExceptionHandler}.
 */
public class DuplicateSkuException extends BusinessException {
  public DuplicateSkuException(String message) {
    super("DUPLICATE_SKU", message);
  }
}
