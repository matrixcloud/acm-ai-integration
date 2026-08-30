package org.acm.ca.application.port.in.query;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.acm.ca.domain.conversation.ConversationStatus;

@Data
public class SearchConversationQuery {
  @NotBlank private String customerId;
  private ConversationStatus status;

  @NotNull
  @Min(1)
  private Integer page;

  @NotNull
  @Min(1)
  @Max(100)
  private Integer size;
}
