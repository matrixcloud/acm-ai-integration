package org.acm.ca.interfaces.http.response;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class ConversationDetailResponse {
  private String id;
  private String conversationNo;
  private String customerId;
  private String status;
  private LocalDateTime startedAt;
  private LocalDateTime endedAt;
  private LocalDateTime createdAt;
  private List<MessageResponse> messages;
  private FeedbackResponse feedback;

  @Data
  public static class MessageResponse {
    private String id;
    private Integer seqNo;
    private String role;
    private String content;
    private LocalDateTime createdAt;
  }

  @Data
  public static class FeedbackResponse {
    private String rating;
    private String comment;
    private LocalDateTime submittedAt;
  }
}
