package org.acm.kb.domain.kb;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
import org.acm.kb.domain.shared.AuditMetadata;

/**
 * A document within a knowledge base.
 *
 * <p>Created in {@link DocumentStatus#PROCESSING} on upload; transitions to {@link
 * DocumentStatus#READY} once chunking and vectorization succeed, or {@link
 * DocumentStatus#FAILED} on embedding error.
 */
@Entity
@Table(name = "documents")
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public final class Document extends AuditMetadata {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String documentNo;
  private Long kbId;
  private String name;
  @Enumerated(EnumType.STRING)
  private DocumentStatus status;
  private int chunkCount;

  @Version private Long version;

  /**
   * Factory for a new document in {@link DocumentStatus#PROCESSING}.
   *
   * @param kbId owning knowledge base id
   * @param name original file name
   * @return a new {@link Document} with zero chunks
   */
  public static Document create(Long kbId, String name) {
    Document document = new Document();
    document.documentNo = generateDocumentNo();
    document.kbId = kbId;
    document.name = name;
    document.status = DocumentStatus.PROCESSING;
    document.chunkCount = 0;
    return document;
  }

  /** Marks the document as ready with the given chunk count. */
  public void markReady(int chunkCount) {
    this.status = DocumentStatus.READY;
    this.chunkCount = chunkCount;
  }

  /** Marks the document as failed. */
  public void markFailed() {
    this.status = DocumentStatus.FAILED;
  }

  private static final DateTimeFormatter TIMESTAMP_FORMAT =
      DateTimeFormatter.ofPattern("yyMMddHHmmss");

  private static String generateDocumentNo() {
    String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
    int random = ThreadLocalRandom.current().nextInt(0, 1_000_000);
    return "DOC-" + timestamp + String.format("%06d", random);
  }
}
