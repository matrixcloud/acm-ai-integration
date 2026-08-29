package org.acm.ca.application.exception;

import org.acm.ca.domain.shared.BusinessException;

/** Thrown when the LLM call itself fails or returns empty content (infrastructure failure, not a tool failure). */
public class LlmUnavailableException extends BusinessException {
  public LlmUnavailableException(String message) {
    super("LLM_UNAVAILABLE", message);
  }
}