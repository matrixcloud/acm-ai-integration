package org.acm.kb.domain.kb;

import org.acm.kb.domain.shared.BusinessException;

/**
 * Thrown when a referenced document does not exist.
 *
 * <p>Maps to HTTP 404 via {@code GlobalExceptionHandler} (code {@code DOCUMENT_NOT_FOUND}).
 */
public class DocumentNotFoundException extends BusinessException {
  public DocumentNotFoundException(String message) {
    super("DOCUMENT_NOT_FOUND", message);
  }
}
