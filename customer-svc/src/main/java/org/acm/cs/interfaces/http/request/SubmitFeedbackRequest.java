package org.acm.cs.interfaces.http.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.acm.cs.domain.conversation.FeedbackRating;

@Data
public class SubmitFeedbackRequest {
  @NotNull private FeedbackRating rating;
  @Size(max = 500) private String comment;
}
