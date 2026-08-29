package org.acm.kb.domain.eval;

import org.acm.kb.domain.shared.BusinessException;

/**
 * Thrown when a referenced evaluation suite does not exist.
 *
 * <p>Maps to HTTP 404 via {@code GlobalExceptionHandler} (code {@code EVAL_SUITE_NOT_FOUND}).
 */
public class EvaluationSuiteNotFoundException extends BusinessException {
  public EvaluationSuiteNotFoundException(String message) {
    super("EVAL_SUITE_NOT_FOUND", message);
  }
}
