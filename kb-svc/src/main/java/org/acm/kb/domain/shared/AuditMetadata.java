package org.acm.kb.domain.shared;

import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Shared audit columns mapped to {@code created_at} / {@code updated_at} columns present on every
 * business table.
 *
 * <p>Maps to the schema columns; the {@code created_by}/{@code updated_by} audit columns are not
 * present in the current schema and are therefore omitted rather than silently left unmapped.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Data
public class AuditMetadata {
  @CreatedDate private LocalDateTime createdAt;
  @LastModifiedDate private LocalDateTime updatedAt;
}
