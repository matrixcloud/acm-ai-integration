package org.acm.ca.application.port.out;

/**
 * Marks KB search failures that are transient and safe to retry: connection failures, read
 * timeouts, HTTP 429/502/503/504.
 */
public class TransientKbSearchException extends KbSearchUnavailableException {
  public TransientKbSearchException(String message, Throwable cause) {
    super(message, cause);
  }
}
