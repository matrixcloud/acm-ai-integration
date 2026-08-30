package org.acm.kb.domain.shared;

/**
 * Base class for business rule violations across all bounded capabilities.
 *
 * <p>Carries a stable error {@code code} that {@code GlobalExceptionHandler} maps to an HTTP
 * status, so layer errors stay decoupled from transport concerns. Lives in {@code domain.shared}
 * (not under a single capability package) because domain, application, and adapter layers all throw
 * it.
 */
public abstract class BusinessException extends RuntimeException {
  private final String code;

  protected BusinessException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
