package org.acm.ca.domain.shared;

/**
 * Base class for business rule violations. Carries a stable error {@code code} that
 * {@code GlobalExceptionHandler} maps to an HTTP status.
 */
public abstract class BusinessException extends RuntimeException {
  private final String code;

  protected BusinessException(String code, String message) {
    super(message);
    this.code = code;
  }

  protected BusinessException(String code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
