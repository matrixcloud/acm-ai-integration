package org.acm.cs.domain.conversation;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.acm.cs.domain.shared.AuditMetadata;

@Entity
@Table(
    name = "messages",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_messages_conversation_seq", columnNames = {"conversation_id", "seq_no"}),
    })
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public final class Message extends AuditMetadata {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Integer seqNo;
  @Enumerated(EnumType.STRING)
  private MessageRole role;
  private String content;
}
