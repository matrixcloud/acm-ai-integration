package org.acm.cs.application.port.out;

import org.acm.cs.domain.shared.BusinessException;

public class OrderQueryUnavailableException extends BusinessException {
  public OrderQueryUnavailableException(String message) {
    super("EXTERNAL_DEPENDENCY_FAILED", message);
  }
}
