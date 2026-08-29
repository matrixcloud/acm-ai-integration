package org.acm.cs.application.exception;

import org.acm.cs.domain.shared.BusinessException;

public class IdempotencyKeyReuseException extends BusinessException {
  public IdempotencyKeyReuseException(String message) {
    super("IDEMPOTENCY_KEY_REUSED", message);
  }
}
