package org.acm.os.application.exception;

import org.acm.os.domain.shared.BusinessException;

/**
 * Thrown when an idempotency key is reserved by a concurrent writer with the same request hash.
 *
 * <p>The caller should abort execution; the concurrent writer's result will be available for replay
 * once it completes. Maps to HTTP 409 Conflict via {@code GlobalExceptionHandler} (code {@code
 * IDEMPOTENCY_KEY_REUSED}).
 */
public class ReservedByConcurrentWriterException extends BusinessException {
  public ReservedByConcurrentWriterException(String message) {
    super("IDEMPOTENCY_KEY_REUSED", message);
  }

  public ReservedByConcurrentWriterException(String message, Throwable cause) {
    super("IDEMPOTENCY_KEY_REUSED", message);
    initCause(cause);
  }
}
