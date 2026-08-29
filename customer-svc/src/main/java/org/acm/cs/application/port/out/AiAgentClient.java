package org.acm.cs.application.port.out;
import java.time.LocalDateTime;
import java.util.List;
import org.acm.cs.application.port.out.OrderQueryClient.OrderSummary;
import org.acm.cs.domain.conversation.MessageRole;

public interface AiAgentClient {

  AgentReply generate(ReplyRequest request);

  record ReplyRequest(
      String conversationNo,
      String customerId,
      List<MessageContext> recentMessages,
      List<OrderSummary> recentOrders,
      String customerMessage) {}

  record MessageContext(MessageRole role, String content, LocalDateTime createdAt) {}

  record AgentReply(String content) {}
}
