package org.acm.ca.interfaces.http.request;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AgentReplyRequest(
    String conversationNo,
    String customerId,
    List<MessageContext> recentMessages,
    List<OrderSummary> recentOrders,
    @NotBlank String customerMessage) {

  public record MessageContext(String role, String content, LocalDateTime createdAt) {}

  public record OrderSummary(
      String orderNo,
      String status,
      BigDecimal payableTotal,
      String currency,
      LocalDateTime createdAt) {}
}
