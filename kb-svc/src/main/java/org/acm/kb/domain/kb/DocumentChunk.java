package org.acm.kb.domain.kb;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.acm.common.persistence.UUIDv7Sequence;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Metadata for a single document chunk.
 *
 * <p>The chunk's vector representation lives in pgvector (managed by Spring AI {@code
 * VectorStore}); this entity tracks only the sequence number and content text for JPA-level
 * bookkeeping and deletion.
 */
@Entity
@Table(name = "document_chunks")
@EntityListeners(AuditingEntityListener.class)
@EqualsAndHashCode(callSuper = false)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public final class DocumentChunk {
  @Id @UUIDv7Sequence private String id;

  private String documentId;
  private int seqNo;
  private String content;

  @CreatedDate private LocalDateTime createdAt;

  public static DocumentChunk of(String documentId, int seqNo, String content) {
    DocumentChunk chunk = new DocumentChunk();
    chunk.documentId = documentId;
    chunk.seqNo = seqNo;
    chunk.content = content;
    return chunk;
  }
}
