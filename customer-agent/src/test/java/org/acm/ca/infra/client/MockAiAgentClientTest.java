package org.acm.ca.infra.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.acm.ca.application.port.in.ReplyStream;
import org.acm.ca.application.port.out.AiAgentClient.ReplyRequest;
import org.acm.ca.application.port.out.AiAgentUnavailableException;
import org.acm.ca.application.port.out.OrderQueryClient.OrderSummary;
import org.junit.jupiter.api.Test;

class MockAiAgentClientTest {

  private final MockAiAgentClient client = new MockAiAgentClient();

  @Test
  void streamsMatchedRuleViaDoneEvent() {
    RecordingStream stream = new RecordingStream();

    client.streamReply(reply("如何申请退款？", List.of()), stream);

    assertThat(stream.done)
        .isEqualTo("退款申请可以在订单详情页提交，审核通过后将原路退回。");
    assertThat(stream.chunks).isEmpty();
    assertThat(stream.errorCode).isNull();
  }

  @Test
  void streamsContextualReplyWhenNoRuleMatches() {
    OrderSummary order =
        new OrderSummary("ORD-1", "PAID", BigDecimal.TEN, "CNY", LocalDateTime.now());
    RecordingStream stream = new RecordingStream();

    client.streamReply(reply("随便聊聊", List.of(order)), stream);

    assertThat(stream.done)
        .isEqualTo("您最近的订单 ORD-1 当前状态为 PAID，应付金额 10 CNY。如有其他问题请随时告诉我。");
  }

  @Test
  void setReplyRuleAddsMatchableRule() {
    client.setReplyRule("投诉", "已记录您的投诉。");
    RecordingStream stream = new RecordingStream();

    client.streamReply(reply("我要投诉", List.of()), stream);

    assertThat(stream.done).isEqualTo("已记录您的投诉。");
  }

  @Test
  void failureFlagThrowsOnceThenResets() {
    client.setFailure(true);

    assertThatThrownBy(() -> client.streamReply(reply("Hello", List.of()), new RecordingStream()))
        .isInstanceOf(AiAgentUnavailableException.class);

    RecordingStream stream = new RecordingStream();
    client.streamReply(reply("Hello", List.of()), stream);
    assertThat(stream.done).isNotBlank();
  }

  private static ReplyRequest reply(String message, List<OrderSummary> orders) {
    return new ReplyRequest("CON-1", "customer-001", List.of(), orders, message);
  }

  private static final class RecordingStream implements ReplyStream {

    private final List<String> chunks = new ArrayList<>();
    private String done;
    private String errorCode;
    private String errorDetail;

    @Override
    public void emitChunk(String token) {
      chunks.add(token);
    }

    @Override
    public void emitDone(String fullContent) {
      this.done = fullContent;
    }

    @Override
    public void emitError(String code, String detail) {
      this.errorCode = code;
      this.errorDetail = detail;
    }
  }
}
