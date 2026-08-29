package org.acm.ca.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.util.Map;
import org.acm.ca.application.idempotency.IdempotencyRecordRepository;
import org.acm.ca.domain.conversation.Conversation;
import org.acm.ca.domain.conversation.ConversationRepository;
import org.acm.ca.domain.conversation.ConversationStatus;
import org.acm.ca.domain.conversation.Feedback;
import org.acm.ca.domain.conversation.FeedbackRating;
import org.acm.ca.domain.conversation.Message;
import org.acm.ca.domain.conversation.MessageRole;
import org.acm.ca.domain.quickquestion.QuickQuestion;
import org.acm.ca.domain.quickquestion.QuickQuestionRepository;
import org.acm.ca.infra.AuditingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  AuditingConfig.class,
  IdempotencyService.class,
  PostgresPersistenceIntegrationTest.JacksonTestConfiguration.class
})
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PostgresPersistenceIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

  @Autowired private ConversationRepository conversationRepository;
  @Autowired private QuickQuestionRepository quickQuestionRepository;
  @Autowired private IdempotencyRecordRepository idempotencyRecordRepository;
  @Autowired private IdempotencyService idempotencyService;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanDatabase() {
    conversationRepository.deleteAll();
    idempotencyRecordRepository.deleteAll();
  }

  @Test
  void flywayCreatesExpectedSchema() {
    Integer migrationCount =
        jdbcTemplate.queryForObject(
            "select count(*) from flyway_schema_history where version = '1' and success",
            Integer.class);
    assertThat(migrationCount).isEqualTo(1);
  }

  @Test
  void persistsAndLoadsConversationAggregateWithAuditing() {
    Conversation conversation = Conversation.create("customer-001");
    conversation.addCustomerMessage("我的订单到哪了？");
    conversation.addAgentReply("您好，您的订单正在配送中。");

    Conversation saved = conversationRepository.saveAndFlush(conversation);
    entityManager.clear();

    Conversation loaded =
        conversationRepository.findByConversationNo(saved.getConversationNo()).orElseThrow();
    assertThat(loaded.getId()).isNotNull();
    assertThat(loaded.getStatus()).isEqualTo(ConversationStatus.ACTIVE);
    assertThat(loaded.getStartedAt()).isNotNull();
    assertThat(loaded.getCreatedAt()).isNotNull();
    assertThat(loaded.getUpdatedAt()).isNotNull();
    assertThat(loaded.getMessages()).hasSize(2);
    assertThat(loaded.getMessages().get(0).getSeqNo()).isEqualTo(1);
    assertThat(loaded.getMessages().get(0).getRole()).isEqualTo(MessageRole.CUSTOMER);
    assertThat(loaded.getMessages().get(0).getContent()).isEqualTo("我的订单到哪了？");
    assertThat(loaded.getMessages().get(1).getSeqNo()).isEqualTo(2);
    assertThat(loaded.getMessages().get(1).getRole()).isEqualTo(MessageRole.AGENT);
    assertThat(loaded.getFeedback()).isNull();
  }

  @Test
  void persistsConversationLifecycleToEndedWithFeedback() {
    Conversation conversation = Conversation.create("customer-001");
    conversation.addCustomerMessage("你好");
    conversation.addAgentReply("您好");
    conversation.end();
    conversation.submitFeedback(FeedbackRating.SATISFIED, "回复很快");

    Conversation saved = conversationRepository.saveAndFlush(conversation);
    entityManager.clear();

    Conversation loaded =
        conversationRepository.findByConversationNo(saved.getConversationNo()).orElseThrow();
    assertThat(loaded.getStatus()).isEqualTo(ConversationStatus.ENDED);
    assertThat(loaded.getEndedAt()).isNotNull();
    assertThat(loaded.getFeedback()).isNotNull();
    assertThat(loaded.getFeedback().getRating()).isEqualTo(FeedbackRating.SATISFIED);
    assertThat(loaded.getFeedback().getComment()).isEqualTo("回复很快");
    assertThat(loaded.getFeedback().getSubmittedAt()).isNotNull();
  }

  @Test
  void databaseEnforcesMessageSeqNoUniqueness() {
    Conversation conversation = Conversation.create("customer-001");
    conversation.addCustomerMessage("first");
    Conversation saved = conversationRepository.saveAndFlush(conversation);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "insert into messages (conversation_id, seq_no, role, content) values (?, ?, ?, ?)",
                    saved.getId(),
                    1,
                    "CUSTOMER",
                    "duplicate"))
        .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
  }

  @Test
  void databaseRejectsEmptyMessageContent() {
    Conversation conversation = Conversation.create("customer-001");
    Conversation saved = conversationRepository.saveAndFlush(conversation);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "insert into messages (conversation_id, seq_no, role, content) values (?, ?, ?, ?)",
                    saved.getId(),
                    1,
                    "CUSTOMER",
                    ""))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void databaseEnforcesSingleFeedbackPerConversation() {
    Conversation conversation = Conversation.create("customer-001");
    conversation.end();
    conversation.submitFeedback(FeedbackRating.SATISFIED, null);
    Conversation saved = conversationRepository.saveAndFlush(conversation);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "insert into feedback (conversation_id, rating, submitted_at) values (?, ?, ?)",
                    saved.getId(),
                    "DISSATISFIED",
                    java.time.LocalDateTime.now()))
        .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
  }

  @Test
  void persistedQuickQuestionsSeedDataExists() {
    long enabledCount =
        quickQuestionRepository.findByEnabledTrueOrderBySortOrderAsc().size();
    assertThat(enabledCount).isGreaterThanOrEqualTo(5);
    assertThat(
            quickQuestionRepository.findByEnabledTrueOrderBySortOrderAsc().stream()
                .map(QuickQuestion::getQuestionText))
        .contains("我的订单到哪了？");
  }

  @Test
  void failedIdempotentActionRollsBackReservedKey() {
    IdempotencyService.IdempotentOperation<String> operation =
        new IdempotencyService.IdempotentOperation<>(
            "send-message", "rollback-key", Map.of("conversationNo", "CON-1"), String.class);

    assertThatThrownBy(
            () ->
                idempotencyService.execute(
                    operation,
                    () -> {
                      throw new IllegalStateException("business action failed");
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("business action failed");

    assertThat(
            idempotencyRecordRepository.findByOperationAndIdempotencyKey(
                "send-message", "rollback-key"))
        .isEmpty();
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class JacksonTestConfiguration {
    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }
}
