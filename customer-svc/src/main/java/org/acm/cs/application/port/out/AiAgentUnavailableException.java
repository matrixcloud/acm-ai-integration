package org.acm.cs.application.port.out;

import org.acm.cs.domain.shared.BusinessException;

public class AiAgentUnavailableException extends BusinessException {
  public AiAgentUnavailableException(String message) {
    super("EXTERNAL_DEPENDENCY_FAILED", message);
  }
}
