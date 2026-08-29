package org.acm.ca.domain.conversation;

import org.acm.ca.domain.shared.BusinessException;

public class FeedbackAlreadySubmittedException extends BusinessException {
  public FeedbackAlreadySubmittedException(String message) {
    super("FEEDBACK_ALREADY_SUBMITTED", message);
  }
}
