package org.acm.kb.domain.eval;

import org.acm.kb.domain.shared.BusinessException;

/**
 * Thrown when a referenced evaluation run does not exist.
 *
 * <p>Maps to HTTP 404 via {@code GlobalExceptionHandler} (code {@code EVAL_RUN_NOT_FOUND}).
 */
public class EvaluationRunNotFoundException extends BusinessException {
  public EvaluationRunNotFoundException(String message) {
    super("EVAL_RUN_NOT_FOUND", message);
  }
}
