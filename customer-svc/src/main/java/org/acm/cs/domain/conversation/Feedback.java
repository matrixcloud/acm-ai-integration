package org.acm.cs.domain.conversation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.acm.cs.domain.shared.AuditMetadata;

@Entity
@Table(
    name = "feedback",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_feedback_conversation", columnNames = {"conversation_id"}),
    })
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public final class Feedback extends AuditMetadata {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  private FeedbackRating rating;
  private String comment;
  private LocalDateTime submittedAt;

  // The feedback table owns the conversation foreign key; the reverse side of the
  // bidirectional OneToOne is mapped on Conversation.feedback (mappedBy).
  // Excluded from toString/equals to avoid cycles through the Conversation aggregate
  // and from JSON serialization to break the Conversation -> Feedback -> Conversation cycle
  // (the conversation is reachable from the aggregate root during idempotency caching).
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "conversation_id", nullable = false)
  @JsonIgnore
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  private Conversation conversation;
}