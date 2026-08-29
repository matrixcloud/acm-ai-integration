package org.acm.cs.domain.conversation;

import org.acm.cs.domain.shared.BusinessException;

public class ConversationStateConflictException extends BusinessException {
  public ConversationStateConflictException(String message) {
    super("CONVERSATION_STATE_CONFLICT", message);
  }
}
