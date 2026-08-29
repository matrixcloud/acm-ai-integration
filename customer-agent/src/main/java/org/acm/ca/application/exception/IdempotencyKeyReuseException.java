package org.acm.ca.application.exception;

import org.acm.ca.domain.shared.BusinessException;

public class IdempotencyKeyReuseException extends BusinessException {
  public IdempotencyKeyReuseException(String message) {
    super("IDEMPOTENCY_KEY_REUSED", message);
  }
}
