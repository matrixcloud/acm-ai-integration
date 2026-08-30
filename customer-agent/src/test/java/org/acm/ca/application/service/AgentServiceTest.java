package org.acm.ca.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.observation.ObservationRegistry;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.acm.ca.application.port.in.GenerateReplyCommand;
import org.acm.ca.application.port.in.ReplyStream;
import org.acm.ca.application.rule.ReplyRule;
import org.acm.ca.application.rule.ReplyRulesConfig;
import org.acm.ca.application.rule.RuleRouter;
import org.acm.ca.domain.shared.InvalidRequestException;
import org.acm.ca.infra.llm.KbSearchTool;
import org.acm.ca.infra.llm.OrderQueryTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

class AgentServiceTest {

  private ChatClient chatClient;
  private ChatClient.ChatClientRequestSpec requestSpec;
  private ChatClient.StreamResponseSpec streamSpec;
  private KbSearchTool kbSearchTool;
  private OrderQueryTool orderQueryTool;
  private AgentService service;

  @BeforeEach
  void setUp() {
    chatClient = mock(ChatClient.class);
    requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
    streamSpec = mock(ChatClient.StreamResponseSpec.class);
    when(chatClient.prompt()).thenReturn(requestSpec);
    when(requestSpec.system(anyString())).thenReturn(requestSpec);
    when(requestSpec.user(anyString())).thenReturn(requestSpec);
    when(requestSpec.tools(any(Object[].class))).thenReturn(requestSpec);
    when(requestSpec.stream()).thenReturn(streamSpec);

    kbSearchTool = mock(KbSearchTool.class);
    orderQueryTool = mock(OrderQueryTool.class);
    ReplyRulesConfig config = configWithRefundRule();
    service =
        new AgentService(
            chatClient,
            new RuleRouter(config),
            kbSearchTool,
            orderQueryTool,
            config,
            ObservationRegistry.create());
  }

  private static ReplyRulesConfig configWithRefundRule() {
    return new ReplyRulesConfig(
        "default-prompt", "KB-1", 5, List.of(new ReplyRule("REFUND", "退款,退货", "refund-prompt", 8)));
  }

  private static GenerateReplyCommand command(String message) {
    return new GenerateReplyCommand("C001", "customer-1", null, null, message);
  }

  @Test
  void blankMessageThrowsInvalidRequest() {
    ReplyStream stream = mock(ReplyStream.class);
    assertThatThrownBy(() -> service.streamReply(command("  "), stream))
        .isInstanceOf(InvalidRequestException.class);
    verify(chatClient, never()).prompt();
  }

  @Test
  void ruleHitRegistersOrderQueryToolButNotKbToolAndStreamsChunksAndDone() {
    when(streamSpec.content()).thenReturn(Flux.just("您", "好"));
    ReplyStream stream = mock(ReplyStream.class);

    service.streamReply(command("我要退款"), stream);

    verify(requestSpec).tools(orderQueryTool);
    verify(requestSpec, never()).tools(kbSearchTool);
    verify(stream).emitChunk("您");
    verify(stream).emitChunk("好");
    verify(stream).emitDone("您好");
  }

  @Test
  void ruleMissRegistersOrderAndKbToolsAndStreamsDone() {
    when(streamSpec.content()).thenReturn(Flux.just("请稍候"));
    ReplyStream stream = mock(ReplyStream.class);

    service.streamReply(command("你们有什么活动"), stream);

    verify(requestSpec).tools(orderQueryTool);
    verify(requestSpec).tools(kbSearchTool);
    verify(stream).emitDone("请稍候");
  }

  @Test
  void emptyReplyEmitsError() {
    when(streamSpec.content()).thenReturn(Flux.empty());
    ReplyStream stream = mock(ReplyStream.class);

    service.streamReply(command("我要退款"), stream);

    verify(stream).emitError("LLM_UNAVAILABLE", "LLM returned empty content");
  }

  @Test
  void buildUserMessageIncludesHistoryOrdersAndCustomerMessage() {
    var command =
        new GenerateReplyCommand(
            "C001",
            "customer-1",
            List.of(
                new GenerateReplyCommand.MessageContext(
                    "CUSTOMER", "你好", LocalDateTime.of(2026, 1, 1, 14, 30)),
                new GenerateReplyCommand.MessageContext(
                    "AGENT", "有什么可以帮您", LocalDateTime.of(2026, 1, 1, 14, 31))),
            List.of(
                new GenerateReplyCommand.OrderSummary(
                    "ORD-1",
                    "SHIPPED",
                    new BigDecimal("299.00"),
                    "CNY",
                    LocalDateTime.of(2026, 1, 1, 10, 0))),
            "订单到哪了");

    String message = service.buildUserMessage(command);

    assertThat(message)
        .contains("## 对话历史")
        .contains("[客户] 14:30: 你好")
        .contains("[客服] 14:31: 有什么可以帮您")
        .contains("## 订单信息")
        .contains("ORD-1")
        .contains("SHIPPED")
        .contains("299.00")
        .contains("## 客户消息")
        .contains("订单到哪了");
  }

  @Test
  void buildUserMessageOmitsHistoryAndMarksEmptyOrders() {
    var command = new GenerateReplyCommand("C001", "customer-1", List.of(), List.of(), "你好");

    String message = service.buildUserMessage(command);

    assertThat(message)
        .doesNotContain("## 对话历史")
        .contains("（无订单信息）")
        .contains("## 客户消息")
        .contains("你好");
  }
}
