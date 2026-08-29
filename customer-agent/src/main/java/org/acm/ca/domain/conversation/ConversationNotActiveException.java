package org.acm.ca.domain.conversation;

import org.acm.ca.domain.shared.BusinessException;

public class ConversationNotActiveException extends BusinessException {
  public ConversationNotActiveException(String message) {
    super("CONVERSATION_NOT_ACTIVE", message);
  }
}
