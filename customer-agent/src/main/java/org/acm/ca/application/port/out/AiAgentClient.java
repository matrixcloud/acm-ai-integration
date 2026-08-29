package org.acm.ca.application.port.out;
import java.time.LocalDateTime;
import java.util.List;
import org.acm.ca.application.port.in.ReplyStream;
import org.acm.ca.application.port.out.OrderQueryClient.OrderSummary;
import org.acm.ca.domain.conversation.MessageRole;

public interface AiAgentClient {

  void streamReply(ReplyRequest request, ReplyStream stream);

  record ReplyRequest(
      String conversationNo,
      String customerId,
      List<MessageContext> recentMessages,
      List<OrderSummary> recentOrders,
      String customerMessage) {}

  record MessageContext(MessageRole role, String content, LocalDateTime createdAt) {}
}
