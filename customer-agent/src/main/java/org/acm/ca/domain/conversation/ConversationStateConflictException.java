package org.acm.ca.domain.conversation;

import org.acm.ca.domain.shared.BusinessException;

public class ConversationStateConflictException extends BusinessException {
  public ConversationStateConflictException(String message) {
    super("CONVERSATION_STATE_CONFLICT", message);
  }
}
