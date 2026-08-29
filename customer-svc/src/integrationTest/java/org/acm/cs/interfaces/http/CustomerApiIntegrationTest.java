package org.acm.cs.interfaces.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * End-to-end tests for UC-01..UC-11 (design §15.3): full Spring context + PostgreSQL via
 * Testcontainers, exercising the REST API, Problem Details, HTTP status codes and idempotency.
 */
@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class CustomerApiIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.update("delete from messages");
    jdbcTemplate.update("delete from feedback");
    jdbcTemplate.update("delete from conversations");
    jdbcTemplate.update("delete from idempotency_records");
  }

  // UC-01 创建会话
  @Test
  void createConversationReturnsActiveConversation() throws Exception {
    String body = """
        {"customerId": "customer-001"}
        """;

    mockMvc
        .perform(
            post("/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "create-key-1")
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.conversationNo").isNotEmpty())
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.customerId").value("customer-001"));
  }

  // UC-02 发送消息并获得 AI 回复 (customer-001 has seeded orders in the mock)
  @Test
  void sendMessageSavesCustomerAndAgentMessages() throws Exception {
    String conversationNo = createConversation("customer-001");

    mockMvc
        .perform(
            post("/conversations/{no}/messages", conversationNo)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "msg-key-1")
                .content("""
                    {"content": "我的订单到哪了？"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.conversationNo").value(conversationNo))
        .andExpect(jsonPath("$.messages[0].role").value("CUSTOMER"))
        .andExpect(jsonPath("$.messages[0].content").value("我的订单到哪了？"))
        .andExpect(jsonPath("$.messages[1].role").value("AGENT"))
        .andExpect(jsonPath("$.messages[1].content").isNotEmpty());
  }

  // UC-03 阻止空消息
  @Test
  void blankMessageIsRejectedWithBadRequest() throws Exception {
    String conversationNo = createConversation("customer-001");

    mockMvc
        .perform(
            post("/conversations/{no}/messages", conversationNo)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "msg-key-blank")
                .content("""
                    {"content": "   "}
                    """))
        .andExpect(status().isBadRequest());

    assertMessageCount(conversationNo, 0);
  }

  // UC-04 已结束会话禁止发送消息
  @Test
  void endedConversationRejectsNewMessages() throws Exception {
    String conversationNo = createConversation("customer-001");
    endConversation(conversationNo, "end-key-1");

    mockMvc
        .perform(
            post("/conversations/{no}/messages", conversationNo)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "msg-key-after-end")
                .content("""
                    {"content": "还能说话吗？"}
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONVERSATION_NOT_ACTIVE"));

    assertMessageCount(conversationNo, 0);
  }

  // UC-05 使用快捷问题 (选择快捷问题等价于发送一条客户消息)
  @Test
  void quickQuestionTextCanBeSentAsRegularMessage() throws Exception {
    String conversationNo = createConversation("customer-001");
    String questionText =
        mockMvc
            .perform(get("/quick-questions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].questionText").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
    // list is sorted by sortOrder asc; first question is 我的订单到哪了？ (rule hit -> agent reply)
    String firstQuestion = questionText.split("\"questionText\":\"")[1].split("\"")[0];

    mockMvc
        .perform(
            post("/conversations/{no}/messages", conversationNo)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "msg-key-quick")
                .content("""
                    {"content": "%s"}
                    """.formatted(firstQuestion)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.messages[0].content").value(firstQuestion))
        .andExpect(jsonPath("$.messages[1].role").value("AGENT"));
  }

  // UC-06 查询快捷问题列表
  @Test
  void quickQuestionsAreSortedBySortOrder() throws Exception {
    mockMvc
        .perform(get("/quick-questions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(5))
        .andExpect(jsonPath("$[0].sortOrder").value(1))
        .andExpect(jsonPath("$[0].enabled").doesNotExist());
  }

  // UC-07 查询会话与历史消息
  @Test
  void conversationDetailShowsFullTimeline() throws Exception {
    String conversationNo = createConversation("customer-001");
    sendMessage(conversationNo, "msg-key-1", "我的订单到哪了？");

    mockMvc
        .perform(get("/conversations/{no}", conversationNo))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.conversationNo").value(conversationNo))
        .andExpect(jsonPath("$.messages[0].seqNo").value(1))
        .andExpect(jsonPath("$.messages[1].seqNo").value(2))
        .andExpect(jsonPath("$.messages[0].role").value("CUSTOMER"))
        .andExpect(jsonPath("$.messages[1].role").value("AGENT"));
  }

  // UC-08 结束会话
  @Test
  void endConversationTransitionsToAwaitingFeedback() throws Exception {
    String conversationNo = createConversation("customer-001");

    mockMvc
        .perform(
            post("/conversations/{no}/end", conversationNo)
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
                .header("Idempotency-Key", "fb-key-1")
                .content("""
                    {"rating": "SATISFIED", "comment": "回复很快"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ENDED"))
        .andExpect(jsonPath("$.feedback.rating").value("SATISFIED"))
        .andExpect(jsonPath("$.feedback.comment").value("回复很快"));
  }

  // UC-10 重复发送幂等
  @Test
  void replayingSameKeyDoesNotDuplicateMessages() throws Exception {
    String conversationNo = createConversation("customer-001");

    String first =
        mockMvc
            .perform(
                post("/conversations/{no}/messages", conversationNo)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", "dup-key")
                    .content("""
                        {"content": "我的订单到哪了？"}
                        """))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String replay =
        mockMvc
            .perform(
                post("/conversations/{no}/messages", conversationNo)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", "dup-key")
                    .content("""
                        {"content": "我的订单到哪了？"}
                        """))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(replay).isEqualTo(first);
    assertMessageCount(conversationNo, 2);
  }

  // UC-10 (same key, different request) -> 409 IDEMPOTENCY_KEY_REUSED
  @Test
  void sameKeyWithDifferentBodyIsRejected() throws Exception {
    String conversationNo = createConversation("customer-001");
    sendMessage(conversationNo, "conflict-key", "我的订单到哪了？");

    mockMvc
        .perform(
            post("/conversations/{no}/messages", conversationNo)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "conflict-key")
                .content("""
                    {"content": "不同的内容"}
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

    assertMessageCount(conversationNo, 2);
  }

  // UC-11 外部调用失败 -> 502, no message saved, same key can retry
  @Test
  void externalFailureReturnsBadGatewayAndAllowsRetry() throws Exception {
    String conversationNo = createConversation("customer-001");
    failCapability("ai-agent");

    mockMvc
        .perform(
            post("/conversations/{no}/messages", conversationNo)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "retry-key")
                .content("""
                    {"content": "我的订单到哪了？"}
                    """))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("EXTERNAL_DEPENDENCY_FAILED"));

    assertMessageCount(conversationNo, 0);

    mockMvc
        .perform(
            post("/conversations/{no}/messages", conversationNo)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "retry-key")
                .content("""
                    {"content": "我的订单到哪了？"}
                    """))
        .andExpect(status().isCreated());
    assertMessageCount(conversationNo, 2);
  }


  // §17: OpenAPI must expose conversation, message, quick-question, end and feedback operations.
  @Test
  void openApiDocumentsAllConversationOperations() throws Exception {
    String spec =
        mockMvc
            .perform(get("/v3/api-docs").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(spec).contains("/conversations", "/conversations/{conversationNo}/messages",
        "/conversations/{conversationNo}/end", "/conversations/{conversationNo}/feedback",
        "/quick-questions");
  }

  private String createConversation(String customerId) throws Exception {
    String response =
        mockMvc
            .perform(
                post("/conversations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", "create-" + customerId + "-" + System.nanoTime())
                    .content("""
                        {"customerId": "%s"}
                        """.formatted(customerId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return response.split("\"conversationNo\":\"")[1].split("\"")[0];
  }

  private void endConversation(String conversationNo, String key) throws Exception {
    mockMvc
        .perform(post("/conversations/{no}/end", conversationNo).header("Idempotency-Key", key))
        .andExpect(status().isAccepted());
  }

  private void sendMessage(String conversationNo, String key, String content) throws Exception {
    mockMvc
        .perform(
            post("/conversations/{no}/messages", conversationNo)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", key)
                .content("""
                    {"content": "%s"}
                    """.formatted(content)))
        .andExpect(status().isCreated());
  }

  private void failCapability(String capability) throws Exception {
    mockMvc
        .perform(
            put("/mock/failures/{capability}", capability)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"enabled": true}
                    """))
        .andExpect(status().isNoContent());
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
}