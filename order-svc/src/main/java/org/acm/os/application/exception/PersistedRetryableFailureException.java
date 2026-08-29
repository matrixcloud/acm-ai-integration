package org.acm.os.application.exception;

public final class PersistedRetryableFailureException extends RuntimeException {
  public PersistedRetryableFailureException(RuntimeException cause) {
    super(cause);
  }

  public RuntimeException original() {
    return (RuntimeException) getCause();
  }
}
