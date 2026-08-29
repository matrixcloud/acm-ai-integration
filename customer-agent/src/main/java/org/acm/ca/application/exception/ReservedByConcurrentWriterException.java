package org.acm.ca.application.exception;

import org.acm.ca.domain.shared.BusinessException;

public class ReservedByConcurrentWriterException extends BusinessException {
  public ReservedByConcurrentWriterException(String message) {
    super("IDEMPOTENCY_KEY_REUSED", message);
  }

  public ReservedByConcurrentWriterException(String message, Throwable cause) {
    super("IDEMPOTENCY_KEY_REUSED", message);
    initCause(cause);
  }
}
