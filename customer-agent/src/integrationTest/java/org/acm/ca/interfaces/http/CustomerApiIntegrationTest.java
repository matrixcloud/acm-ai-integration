package org.acm.ca.interfaces.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.acm.ca.application.port.in.AgentUseCase;
import org.acm.ca.application.port.in.GenerateReplyCommand;
import org.acm.ca.application.port.in.ReplyStream;
import org.acm.ca.application.port.out.AiAgentUnavailableException;
import org.acm.ca.application.port.out.OrderQueryClient;
import org.acm.ca.application.port.out.OrderQueryClient.OrderDetail;
import org.acm.ca.application.port.out.OrderQueryClient.OrderSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end tests for the merged customer service API: full Spring context + PostgreSQL via
 * Testcontainers, exercising the streaming message endpoint (SSE), sync endpoints, Problem Details,
 * API versioning and idempotency.
 */
@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {"eureka.client.enabled=false", "spring.ai.openai.api-key=test-key"})
class CustomerApiIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private TestAgentUseCase agentUseCase;

  @BeforeEach
  void cleanDatabase() {
    agentUseCase.reset();
    jdbcTemplate.update("delete from messages");
    jdbcTemplate.update("delete from feedback");
    jdbcTemplate.update("delete from conversations");
    jdbcTemplate.update("delete from idempotency_records");
  }

  // UC-01 创建会话
  @Test
  void createConversationReturnsActiveConversation() throws Exception {
    String body =
        """
        {"customerId": "customer-001"}
        """;

    mockMvc
        .perform(
            post("/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                .header("API-Version", "1")
                .header("Idempotency-Key", "create-key-1")
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.conversationNo").isNotEmpty())
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.customerId").value("customer-001"));
  }

  // UC-02 流式发送消息：done 事件返回持久化后的 MessageThread
  @Test
  void sendMessageStreamsDoneEventWithPersistedThread() throws Exception {
    String conversationNo = createConversation("customer-001");

    SseResult result = sendMessageSse(conversationNo, "msg-key-1", "我的订单到哪了？");

    assertThat(result.chunks()).isEmpty();
    assertThat(result.done()).contains("\"conversationNo\":\"%s\"".formatted(conversationNo));
    assertThat(result.done()).contains("\"role\":\"CUSTOMER\"");
    assertThat(result.done()).contains("\"content\":\"我的订单到哪了？\"");
    assertThat(result.done()).contains("\"role\":\"AGENT\"");
    assertThat(result.errorCode()).isNull();
    assertMessageCount(conversationNo, 2);
  }

  // UC-07 阻止空消息（流前 problem+json）
  @Test
  void blankMessageIsRejectedBeforeTheStreamStarts() throws Exception {
    String conversationNo = createConversation("customer-001");

    mockMvc
        .perform(
            post("/conversations/{no}/messages", conversationNo)
                .contentType(MediaType.APPLICATION_JSON)
                .header("API-Version", "1")
                .header("Idempotency-Key", "msg-key-blank")
                .content(
                    """
                    {"content": "   "}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(request().asyncNotStarted())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    assertMessageCount(conversationNo, 0);
  }

  @Test
  void missingIdempotencyKeyIsRejectedBeforeTheStreamStarts() throws Exception {
    String conversationNo = createConversation("customer-001");

    mockMvc
        .perform(
            post("/conversations/{no}/messages", conversationNo)
                .contentType(MediaType.APPLICATION_JSON)
                .header("API-Version", "1")
                .content(
                    """
                    {"content": "Hello"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MISSING_REQUEST_HEADER"));

    assertMessageCount(conversationNo, 0);
  }

  // UC-06 会话状态冲突（流中 error 事件）
  @Test
  void endedConversationRejectsNewMessagesViaErrorEvent() throws Exception {
    String conversationNo = createConversation("customer-001");
    endConversation(conversationNo, "end-key-1");

    SseResult result = sendMessageSse(conversationNo, "msg-key-after-end", "还能说话吗？");

    assertThat(result.done()).isNull();
    assertThat(result.errorCode()).isEqualTo("CONVERSATION_NOT_ACTIVE");
    assertMessageCount(conversationNo, 0);
  }

  // UC-08 使用快捷问题 (选择快捷问题等价于发送一条客户消息)
  @Test
  void quickQuestionTextCanBeSentAsRegularMessage() throws Exception {
    String conversationNo = createConversation("customer-001");
    String questionText =
        mockMvc
            .perform(get("/quick-questions").header("API-Version", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].questionText").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    // list is sorted by sortOrder asc; first question is 我的订单到哪了？ (rule hit -> agent reply)
    String firstQuestion = questionText.split("\"questionText\":\"")[1].split("\"")[0];

    SseResult result = sendMessageSse(conversationNo, "msg-key-quick", firstQuestion);

    assertThat(result.done()).contains(firstQuestion);
    assertThat(result.done()).contains("\"role\":\"AGENT\"");
  }

  // UC-06 查询快捷问题列表
  @Test
  void quickQuestionsAreSortedBySortOrder() throws Exception {
    mockMvc
        .perform(get("/quick-questions").header("API-Version", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(5))
        .andExpect(jsonPath("$[0].sortOrder").value(1))
        .andExpect(jsonPath("$[0].enabled").doesNotExist());
  }

  // UC-07 查询会话与历史消息
  @Test
  void conversationDetailShowsFullTimeline() throws Exception {
    String conversationNo = createConversation("customer-001");
    sendMessageSse(conversationNo, "msg-key-1", "我的订单到哪了？");

    mockMvc
        .perform(get("/conversations/{no}", conversationNo).header("API-Version", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.conversationNo").value(conversationNo))
        .andExpect(jsonPath("$.messages[0].seqNo").value(1))
        .andExpect(jsonPath("$.messages[1].seqNo").value(2))
        .andExpect(jsonPath("$.messages[0].role").value("CUSTOMER"))
        .andExpect(jsonPath("$.messages[1].role").value("AGENT"));
  }

  @Test
  void missingApiVersionIsRejected() throws Exception {
    mockMvc
        .perform(get("/quick-questions"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("UNSUPPORTED_API_VERSION"));
  }

  // UC-08 结束会话
  @Test
  void endConversationTransitionsToAwaitingFeedback() throws Exception {
    String conversationNo = createConversation("customer-001");

    mockMvc
        .perform(
            post("/conversations/{no}/end", conversationNo)
                .header("API-Version", "1")
                .header("Idempotency-Key", "end-key-1"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("AWAITING_FEEDBACK"));
  }

  // UC-09 提交会话评价
  @Test
  void submitFeedbackEndsConversation() throws Exception {
    String conversationNo = createConversation("customer-001");
    endConversation(conversationNo, "end-key-1");

    mockMvc
        .perform(
            post("/conversations/{no}/feedback", conversationNo)
                .contentType(MediaType.APPLICATION_JSON)
                .header("API-Version", "1")
                .header("Idempotency-Key", "fb-key-1")
                .content(
                    """
                    {"rating": "SATISFIED", "comment": "回复很快"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ENDED"))
        .andExpect(jsonPath("$.feedback.rating").value("SATISFIED"))
        .andExpect(jsonPath("$.feedback.comment").value("回复很快"));
  }

  // UC-03 幂等重放：重放不产生 chunk，直接 done，且响应与首次逐字节一致
  @Test
  void replayingSameKeyReturnsIdenticalStream() throws Exception {
    String conversationNo = createConversation("customer-001");

    String first = sendMessageSse(conversationNo, "dup-key", "我的订单到哪了？").raw();
    String replay = sendMessageSse(conversationNo, "dup-key", "我的订单到哪了？").raw();

    assertThat(replay).isEqualTo(first);
    assertMessageCount(conversationNo, 2);
  }

  // UC-05 同 key 不同 body（流中 error 事件）
  @Test
  void sameKeyWithDifferentBodyIsRejectedViaErrorEvent() throws Exception {
    String conversationNo = createConversation("customer-001");
    sendMessageSse(conversationNo, "conflict-key", "我的订单到哪了？");

    SseResult result = sendMessageSse(conversationNo, "conflict-key", "不同的内容");

    assertThat(result.done()).isNull();
    assertThat(result.errorCode()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
    assertMessageCount(conversationNo, 2);
  }

  // UC-04 外部调用失败 -> error 事件 + 无消息落库 + 同 key 可重试
  @Test
  void externalFailureEmitsErrorEventAndAllowsRetry() throws Exception {
    String conversationNo = createConversation("customer-001");
    agentUseCase.failNext();

    SseResult failure = sendMessageSse(conversationNo, "retry-key", "我的订单到哪了？");

    assertThat(failure.done()).isNull();
    assertThat(failure.errorCode()).isEqualTo("EXTERNAL_DEPENDENCY_FAILED");
    assertMessageCount(conversationNo, 0);

    SseResult retry = sendMessageSse(conversationNo, "retry-key", "我的订单到哪了？");
    assertThat(retry.errorCode()).isNull();
    assertThat(retry.done()).isNotNull();
    assertMessageCount(conversationNo, 2);
  }

  // §17: OpenAPI must expose conversation, message, quick-question, end, feedback and agent
  // operations.
  @Test
  void openApiDocumentsAllConversationOperations() throws Exception {
    String spec =
        mockMvc
            .perform(get("/v3/api-docs").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(spec)
        .contains(
            "/conversations",
            "/conversations/{conversationNo}/messages",
            "/conversations/{conversationNo}/end",
            "/conversations/{conversationNo}/feedback",
            "/quick-questions",
            "/agent/reply");
  }

  private String createConversation(String customerId) throws Exception {
    String response =
        mockMvc
            .perform(
                post("/conversations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("API-Version", "1")
                    .header("Idempotency-Key", "create-" + customerId + "-" + System.nanoTime())
                    .content(
                        """
                        {"customerId": "%s"}
                        """
                            .formatted(customerId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    return response.split("\"conversationNo\":\"")[1].split("\"")[0];
  }

  private void endConversation(String conversationNo, String key) throws Exception {
    mockMvc
        .perform(
            post("/conversations/{no}/end", conversationNo)
                .header("API-Version", "1")
                .header("Idempotency-Key", key))
        .andExpect(status().isAccepted());
  }

  private SseResult sendMessageSse(String conversationNo, String key, String content)
      throws Exception {
    MvcResult asyncResult =
        mockMvc
            .perform(
                post("/conversations/{no}/messages", conversationNo)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("API-Version", "1")
                    .header("Idempotency-Key", key)
                    .content(
                        """
                        {"content": "%s"}
                        """
                            .formatted(content)))
            .andExpect(request().asyncStarted())
            .andReturn();
    String raw =
        mockMvc
            .perform(asyncDispatch(asyncResult))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    return parseSse(raw);
  }

  private SseResult parseSse(String raw) {
    List<String> chunks = new ArrayList<>();
    String done = null;
    String errorCode = null;
    for (String block : raw.strip().split("\n\n")) {
      String event = null;
      StringBuilder data = new StringBuilder();
      for (String line : block.split("\n")) {
        if (line.startsWith("event:")) {
          event = line.substring("event:".length()).strip();
        } else if (line.startsWith("data:")) {
          if (data.length() > 0) {
            data.append('\n');
          }
          data.append(line.substring("data:".length()));
        }
      }
      switch (event == null ? "" : event) {
        case "chunk" -> chunks.add(data.toString());
        case "done" -> done = data.toString();
        case "error" -> errorCode = errorDetail(data.toString()).get("code");
        default -> throw new IllegalStateException("Unexpected SSE block in test: " + block);
      }
    }
    return new SseResult(chunks, done, errorCode, raw);
  }

  @SuppressWarnings("unchecked")
  private java.util.Map<String, String> errorDetail(String json) {
    return objectMapper.readValue(json, java.util.Map.class);
  }

  private void assertMessageCount(String conversationNo, int expected) {
    Integer count =
        jdbcTemplate.queryForObject(
            """
            select count(*) from messages m
            join conversations c on c.id = m.conversation_id
            where c.conversation_no = ?
            """,
            Integer.class,
            conversationNo);
    assertThat(count).isEqualTo(expected);
  }

  private record SseResult(List<String> chunks, String done, String errorCode, String raw) {}

  @TestConfiguration
  static class TestClientsConfig {

    @Bean
    @Primary
    TestAgentUseCase testAgentUseCase() {
      return new TestAgentUseCase();
    }

    @Bean
    @Primary
    OrderQueryClient testOrderQueryClient() {
      return new OrderQueryClient() {
        @Override
        public List<OrderSummary> getRecentOrders(String customerId) {
          return List.of();
        }

        @Override
        public List<OrderSummary> findByRecipientPhone(String recipientPhone) {
          return List.of();
        }

        @Override
        public Optional<OrderDetail> findByOrderNo(String orderNo) {
          return Optional.empty();
        }
      };
    }
  }

  static class TestAgentUseCase implements AgentUseCase {
    private final AtomicBoolean failNext = new AtomicBoolean();

    void reset() {
      failNext.set(false);
    }

    void failNext() {
      failNext.set(true);
    }

    @Override
    public void streamReply(GenerateReplyCommand command, ReplyStream stream) {
      if (failNext.getAndSet(false)) {
        throw new AiAgentUnavailableException("agent down");
      }
      stream.emitDone("自动回复：" + command.customerMessage());
    }
  }
}
