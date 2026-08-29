package org.acm.cs.domain.conversation;

import org.acm.cs.domain.shared.BusinessException;

public class ConversationNotFoundException extends BusinessException {
  public ConversationNotFoundException(String message) {
    super("CONVERSATION_NOT_FOUND", message);
  }
}
