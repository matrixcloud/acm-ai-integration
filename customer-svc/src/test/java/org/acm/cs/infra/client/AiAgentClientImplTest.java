package org.acm.cs.infra.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.acm.cs.application.port.out.AiAgentClient.AgentReply;
import org.acm.cs.application.port.out.AiAgentClient.ReplyRequest;
import org.acm.cs.application.port.out.AiAgentUnavailableException;
import org.acm.cs.application.port.out.OrderQueryClient.OrderSummary;
import org.junit.jupiter.api.Test;

class AiAgentClientImplTest {

  private static final String FALLBACK_REPLY =
      "您好，已收到您的消息，我正在查询相关信息，请稍候。";

  private static final String DEFAULT_ORDER_REPLY_TEMPLATE =
      "您最近的订单 %s 当前状态为 %s，应付金额 %s %s。如有其他问题请随时告诉我。";

  private final AiAgentClientImpl client = new AiAgentClientImpl();

  private static ReplyRequest requestWith(String message, List<OrderSummary> orders) {
    return new ReplyRequest("CONV-1", "customer-001", List.of(), orders, message);
  }

  @Test
  void generateReturnsRuleBasedReplyWhenKeywordMatches() {
    ReplyRequest request = requestWith("我要申请退款", List.of());

    AgentReply reply = client.generate(request);

    assertThat(reply.content()).isEqualTo("退款申请可以在订单详情页提交，审核通过后将原路退回。");
  }

  @Test
  void generateReturnsFallbackWhenNoKeywordMatchesAndNoOrders() {
    ReplyRequest request = requestWith("你好", List.of());

    AgentReply reply = client.generate(request);

    assertThat(reply.content()).isEqualTo(FALLBACK_REPLY);
  }

  @Test
  void generateReturnsOrderContextualReplyWhenOrdersPresent() {
    OrderSummary order =
        new OrderSummary("ORD2608280001", "PAID", new BigDecimal("498.00"), "CNY",
            LocalDateTime.of(2026, 8, 28, 10, 30, 0));
    ReplyRequest request = requestWith("你好", List.of(order));

    AgentReply reply = client.generate(request);

    assertThat(reply.content())
        .isEqualTo(
            DEFAULT_ORDER_REPLY_TEMPLATE.formatted(
                "ORD2608280001", "PAID", "498.00", "CNY"));
  }

  @Test
  void setReplyRuleAddsNewRule() {
    client.setReplyRule("投诉", "您的投诉已记录，我们会尽快处理。");

    AgentReply reply = client.generate(requestWith("我要投诉", List.of()));

    assertThat(reply.content()).isEqualTo("您的投诉已记录，我们会尽快处理。");
  }

  @Test
  void setFailureMakesNextGenerateThrowAndThenResets() {
    client.setFailure(true);

    assertThatThrownBy(() -> client.generate(requestWith("退款", List.of())))
        .isInstanceOf(AiAgentUnavailableException.class)
        .hasMessageContaining("Mock AI Agent configured to fail");

    AgentReply reply = client.generate(requestWith("退款", List.of()));
    assertThat(reply.content()).isEqualTo("退款申请可以在订单详情页提交，审核通过后将原路退回。");
  }
}
