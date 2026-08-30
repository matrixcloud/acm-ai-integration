package org.acm.os.application.exception;

import org.acm.os.domain.shared.BusinessException;

/**
 * Thrown when an idempotency key is replayed with a different request body.
 *
 * <p>Maps to HTTP 409 Conflict via {@code GlobalExceptionHandler} (code {@code
 * IDEMPOTENCY_KEY_REUSED}).
 */
public class IdempotencyKeyReuseException extends BusinessException {
  public IdempotencyKeyReuseException(String message) {
    super("IDEMPOTENCY_KEY_REUSED", message);
  }
}
