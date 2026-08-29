package org.acm.cs.domain.conversation;

import org.acm.cs.domain.shared.BusinessException;

public class ConversationNotActiveException extends BusinessException {
  public ConversationNotActiveException(String message) {
    super("CONVERSATION_NOT_ACTIVE", message);
  }
}
