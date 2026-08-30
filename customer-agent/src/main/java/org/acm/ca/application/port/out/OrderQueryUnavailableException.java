package org.acm.ca.application.port.out;

import org.acm.ca.domain.shared.BusinessException;

public class OrderQueryUnavailableException extends BusinessException {
  public OrderQueryUnavailableException(String message) {
    super("EXTERNAL_DEPENDENCY_FAILED", message);
  }

  public OrderQueryUnavailableException(String message, Throwable cause) {
    super("EXTERNAL_DEPENDENCY_FAILED", message, cause);
  }
}
