package org.acm.kb.domain.kb;

import org.acm.kb.domain.shared.BusinessException;

/**
 * Thrown when a referenced knowledge base does not exist.
 *
 * <p>Maps to HTTP 404 via {@code GlobalExceptionHandler} (code {@code KB_NOT_FOUND}).
 */
public class KnowledgeBaseNotFoundException extends BusinessException {
  public KnowledgeBaseNotFoundException(String message) {
    super("KB_NOT_FOUND", message);
  }
}
