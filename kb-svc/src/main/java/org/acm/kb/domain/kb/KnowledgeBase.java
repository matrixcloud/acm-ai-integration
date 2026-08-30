package org.acm.kb.domain.kb;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.acm.common.persistence.UUIDv7Sequence;
import org.acm.kb.domain.shared.AuditMetadata;
import org.acm.kb.domain.shared.InvalidRequestException;

/**
 * The knowledge base aggregate root.
 *
 * <p>Encapsulates the business invariants for creating a knowledge base: the name must be a
 * non-blank string of at most 100 characters, and the status starts as {@link
 * KnowledgeBaseStatus#ACTIVE}. Document count is maintained by the application service and mutated
 * via {@link #incrementDocCount()}/{@link #decrementDocCount()}.
 */
@Entity
@Table(name = "knowledge_bases")
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public final class KnowledgeBase extends AuditMetadata {
  @Id @UUIDv7Sequence private String id;

  private String kbNo;
  private String name;

  @Enumerated(EnumType.STRING)
  private KnowledgeBaseStatus status;

  private int docCount;

  @Version private Long version;

  /**
   * Factory for a new knowledge base.
   *
   * @param name knowledge base name (non-blank, at most 100 characters)
   * @return a new {@link KnowledgeBase} in {@link KnowledgeBaseStatus#ACTIVE} with zero documents
   */
  public static KnowledgeBase create(String name) {
    String trimmed = trimName(name);
    KnowledgeBase kb = new KnowledgeBase();
    kb.kbNo = generateKbNo();
    kb.name = trimmed;
    kb.status = KnowledgeBaseStatus.ACTIVE;
    kb.docCount = 0;
    return kb;
  }

  /** Archives an active knowledge base; no-op if already archived. */
  public void archive() {
    this.status = KnowledgeBaseStatus.ARCHIVED;
  }

  /** Reactivates an archived knowledge base; no-op if already active. */
  public void activate() {
    this.status = KnowledgeBaseStatus.ACTIVE;
  }

  /** Requires the knowledge base to be active for document uploads. */
  public void requireActive() {
    if (status != KnowledgeBaseStatus.ACTIVE) {
      throw new KnowledgeBaseNotActiveException("Knowledge base %s is not active".formatted(kbNo));
    }
  }

  public void incrementDocCount() {
    this.docCount++;
  }

  public void decrementDocCount() {
    if (this.docCount > 0) {
      this.docCount--;
    }
  }

  private static String trimName(String name) {
    if (name == null) {
      throw new InvalidRequestException("Knowledge base name must not be null");
    }
    String trimmed = name.strip();
    if (trimmed.isEmpty()) {
      throw new InvalidRequestException("Knowledge base name must not be blank");
    }
    if (trimmed.length() > 100) {
      throw new InvalidRequestException("Knowledge base name must not exceed 100 characters");
    }
    return trimmed;
  }

  private static final DateTimeFormatter TIMESTAMP_FORMAT =
      DateTimeFormatter.ofPattern("yyMMddHHmmss");

  private static String generateKbNo() {
    String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
    int random = ThreadLocalRandom.current().nextInt(0, 1_000_000);
    return "KB-" + timestamp + String.format("%06d", random);
  }
}
