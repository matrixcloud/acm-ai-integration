package org.acm.os.application.exception;

public final class RetryableOperationException extends RuntimeException {
  public RetryableOperationException(RuntimeException cause) {
    super(cause);
  }

  public RuntimeException original() {
    return (RuntimeException) getCause();
  }
}
