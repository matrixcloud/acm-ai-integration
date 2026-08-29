package org.acm.ca.domain.conversation;

import org.acm.ca.domain.shared.BusinessException;

public class ConversationNotFoundException extends BusinessException {
  public ConversationNotFoundException(String message) {
    super("CONVERSATION_NOT_FOUND", message);
  }
}
