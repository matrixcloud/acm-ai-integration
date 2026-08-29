package org.acm.ca.interfaces.http.response;

import java.util.List;
import lombok.Data;

@Data
public class MessageThreadResponse {
  private String conversationNo;
  private List<ConversationDetailResponse.MessageResponse> messages;
}
