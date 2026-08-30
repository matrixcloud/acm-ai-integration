package org.acm.ca.application.port.out;

/**
 * Marks order query failures where retrying cannot help: unexpected response status, contract or
 * deserialization violations.
 */
public class OrderQueryContractException extends OrderQueryUnavailableException {
  public OrderQueryContractException(String message) {
    super(message);
  }

  public OrderQueryContractException(String message, Throwable cause) {
    super(message, cause);
  }
}
