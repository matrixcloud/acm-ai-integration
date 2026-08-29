package org.acm.cs.domain.conversation;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.acm.cs.domain.shared.AuditMetadata;
import org.acm.cs.domain.shared.InvalidRequestException;

@Entity
@Table(name = "conversations")
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public final class Conversation extends AuditMetadata {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String conversationNo;
  private String customerId;
  @Enumerated(EnumType.STRING)
  private ConversationStatus status;
  private LocalDateTime startedAt;
  private LocalDateTime endedAt;

  @Version private Long version;

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "conversation_id", nullable = false)
  private List<Message> messages = new ArrayList<>();

  @OneToOne(cascade = CascadeType.ALL)
  @JoinColumn(name = "conversation_id")
  private Feedback feedback;

  public static Conversation create(String customerId) {
    Conversation conversation = new Conversation();
    conversation.conversationNo = generateConversationNo();
    conversation.customerId = customerId;
    conversation.status = ConversationStatus.ACTIVE;
    conversation.startedAt = LocalDateTime.now();
    return conversation;
  }

  public Message addCustomerMessage(String content) {
    requireActive();
    String trimmed = trimContent(content);
    return appendMessage(MessageRole.CUSTOMER, trimmed);
  }

  public Message addAgentReply(String content) {
    requireActive();
    if (content == null || content.isBlank()) {
      throw new InvalidRequestException("Agent reply must not be blank");
    }
    return appendMessage(MessageRole.AGENT, content);
  }

  public void end() {
    if (status != ConversationStatus.ACTIVE) {
      throw new ConversationStateConflictException(
          "Conversation %s is not active, cannot end".formatted(conversationNo));
    }
    this.status = ConversationStatus.AWAITING_FEEDBACK;
    this.endedAt = LocalDateTime.now();
  }

  public void submitFeedback(FeedbackRating rating, String comment) {
    if (status != ConversationStatus.AWAITING_FEEDBACK) {
      throw new ConversationStateConflictException(
          "Conversation %s is not awaiting feedback".formatted(conversationNo));
    }
    if (this.feedback != null) {
      throw new FeedbackAlreadySubmittedException(
          "Feedback already submitted for conversation %s".formatted(conversationNo));
    }
    Feedback newFeedback = new Feedback();
    newFeedback.setRating(rating);
    newFeedback.setComment(comment);
    newFeedback.setSubmittedAt(LocalDateTime.now());
    this.feedback = newFeedback;
    this.status = ConversationStatus.ENDED;
  }

  private void requireActive() {
    if (status != ConversationStatus.ACTIVE) {
      throw new ConversationNotActiveException(
          "Conversation %s is not active".formatted(conversationNo));
    }
  }

  private static String trimContent(String content) {
    if (content == null) {
      throw new InvalidRequestException("Message content must not be null");
    }
    String trimmed = content.strip();
    if (trimmed.isEmpty()) {
      throw new InvalidRequestException("Message content must not be blank");
    }
    return trimmed;
  }

  private Message appendMessage(MessageRole role, String content) {
    Message message = new Message();
    message.setSeqNo(messages.size() + 1);
    message.setRole(role);
    message.setContent(content);
    this.messages.add(message);
    return message;
  }

  public List<Message> getMessages() {
    return Collections.unmodifiableList(messages);
  }

  private static final DateTimeFormatter TIMESTAMP_FORMAT =
      DateTimeFormatter.ofPattern("yyMMddHHmmss");

  private static String generateConversationNo() {
    String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
    int random = ThreadLocalRandom.current().nextInt(0, 1_000_000);
    return "CON" + timestamp + String.format("%06d", random);
  }
}
