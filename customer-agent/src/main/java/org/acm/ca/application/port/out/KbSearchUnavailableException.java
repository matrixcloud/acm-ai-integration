package org.acm.ca.application.port.out;

import org.acm.ca.domain.shared.BusinessException;

/** Thrown by the {@code KbSearchClient} HTTP adapter when the {@code kb-svc} call fails. */
public class KbSearchUnavailableException extends BusinessException {
  public KbSearchUnavailableException(String message) {
    super("EXTERNAL_DEPENDENCY_FAILED", message);
  }
}