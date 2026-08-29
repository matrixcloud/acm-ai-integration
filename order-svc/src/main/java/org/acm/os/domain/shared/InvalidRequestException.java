package org.acm.os.domain.shared;

/**
 * Thrown for semantically invalid input that passes structural validation but violates a business
 * acceptance rule (unknown enum value, unsupported sort field, …).
 *
 * <p>Maps to HTTP 400 via {@code GlobalExceptionHandler} (code {@code INVALID_REQUEST}).
 */
public class InvalidRequestException extends BusinessException {
  public InvalidRequestException(String message) {
    super("INVALID_REQUEST", message);
  }
}
