package org.acm.ca.interfaces.http.response;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ConversationSummaryResponse {
  private String id;
  private String conversationNo;
  private String customerId;
  private String status;
  private LocalDateTime startedAt;
  private LocalDateTime createdAt;
}
