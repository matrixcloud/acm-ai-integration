package org.acm.ca.application.port.in;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Reply-generation command, mirroring the {@code AiAgentClient.ReplyRequest} contract owned by
 * {@code customer-svc}. {@code role} is a plain string because it arrives as JSON.
 */
public record GenerateReplyCommand(
    String conversationNo,
    String customerId,
    List<MessageContext> recentMessages,
    List<OrderSummary> recentOrders,
    String customerMessage) {

  public record MessageContext(String role, String content, LocalDateTime createdAt) {}

  public record OrderSummary(
      String orderNo,
      String status,
      BigDecimal payableTotal,
      String currency,
      LocalDateTime createdAt) {}
}