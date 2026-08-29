package org.acm.cs.infra.client;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.acm.cs.application.port.out.AiAgentClient;
import org.acm.cs.application.port.out.AiAgentUnavailableException;
import org.acm.cs.application.port.out.OrderQueryClient.OrderSummary;
import org.springframework.stereotype.Component;

/**
 * In-memory Mock implementation of {@link AiAgentClient} (design §11.1).
 *
 */
@Component
public class AiAgentClientImpl implements AiAgentClient {

  private static final String FALLBACK_REPLY =
      "您好，已收到您的消息，我正在查询相关信息，请稍候。";

  private static final String DEFAULT_ORDER_REPLY_TEMPLATE =
      "您最近的订单 %s 当前状态为 %s，应付金额 %s %s。如有其他问题请随时告诉我。";

  private final Map<String, String> replyRules = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Boolean> failureFlags = new ConcurrentHashMap<>();

  public AiAgentClientImpl() {
    replyRules.put("退款", "退款申请可以在订单详情页提交，审核通过后将原路退回。");
    replyRules.put("退货", "如需退货，请在确认收货后 7 天内发起退货申请。");
    replyRules.put("发票", "电子发票会在订单完成后 24 小时内发送至您的注册邮箱。");
    replyRules.put("物流", "您可以在订单详情页查看实时物流信息，通常 1-3 个工作日送达。");
    replyRules.put("人工", "正在为您转接人工客服，请稍候。");
  }

  @Override
  public AgentReply generate(ReplyRequest request) {
    if (failureFlags.getOrDefault("ai-agent", false)) {
      failureFlags.put("ai-agent", false);
      throw new AiAgentUnavailableException("Mock AI Agent configured to fail for this call");
    }

    String customerMessage = request.customerMessage();
    String reply = matchRule(customerMessage);
    if (reply == null) {
      reply = buildContextualReply(request);
    }
    return new AgentReply(reply);
  }

  public void setReplyRule(String keyword, String reply) {
    replyRules.put(keyword, reply);
  }

  public void setFailure(boolean shouldFail) {
    failureFlags.put("ai-agent", shouldFail);
  }

  private String matchRule(String message) {
    for (Map.Entry<String, String> entry : replyRules.entrySet()) {
      if (message.contains(entry.getKey())) {
        return entry.getValue();
      }
    }
    return null;
  }

  private String buildContextualReply(ReplyRequest request) {
    List<OrderSummary> orders = request.recentOrders();
    if (orders != null && !orders.isEmpty()) {
      OrderSummary latest = orders.get(0);
      return DEFAULT_ORDER_REPLY_TEMPLATE.formatted(
          latest.orderNo(),
          latest.status(),
          latest.payableTotal(),
          latest.currency());
    }
    return FALLBACK_REPLY;
  }
}