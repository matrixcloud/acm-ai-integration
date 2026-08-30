package org.acm.ca.application.port.out;

/**
 * Marks order query failures that are transient and safe to retry: connection failures, read
 * timeouts, HTTP 429/502/503/504.
 */
public class TransientOrderQueryException extends OrderQueryUnavailableException {
  public TransientOrderQueryException(String message, Throwable cause) {
    super(message, cause);
  }
}
