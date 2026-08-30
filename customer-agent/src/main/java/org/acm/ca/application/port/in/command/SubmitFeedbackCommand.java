package org.acm.ca.application.port.in.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.acm.ca.domain.conversation.FeedbackRating;

@Data
public class SubmitFeedbackCommand {
  @NotBlank private String conversationNo;
  @NotNull private FeedbackRating rating;

  @Size(max = 500)
  private String comment;
}
