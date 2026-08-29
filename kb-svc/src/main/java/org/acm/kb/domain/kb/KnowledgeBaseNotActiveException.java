package org.acm.kb.domain.kb;

import org.acm.kb.domain.shared.BusinessException;

/**
 * Thrown when an operation requires an active knowledge base but the referenced one is archived.
 *
 * <p>Maps to HTTP 409 via {@code GlobalExceptionHandler} (code {@code KB_NOT_ACTIVE}).
 */
public class KnowledgeBaseNotActiveException extends BusinessException {
  public KnowledgeBaseNotActiveException(String message) {
    super("KB_NOT_ACTIVE", message);
  }
}
