package org.acm.cs.domain.conversation;

import org.acm.cs.domain.shared.BusinessException;

public class FeedbackAlreadySubmittedException extends BusinessException {
  public FeedbackAlreadySubmittedException(String message) {
    super("FEEDBACK_ALREADY_SUBMITTED", message);
  }
}
