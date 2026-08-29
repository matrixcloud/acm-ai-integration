package org.acm.os.application.port.out;

import org.acm.os.domain.shared.BusinessException;

public class ExternalDependencyException extends BusinessException {
  public ExternalDependencyException(String message) {
    super("EXTERNAL_DEPENDENCY_FAILED", message);
  }
}
