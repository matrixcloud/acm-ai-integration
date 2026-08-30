package org.acm.ca.domain.quickquestion;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.acm.ca.domain.shared.AuditMetadata;
import org.acm.common.persistence.UUIDv7Sequence;

@Entity
@Table(name = "quick_questions")
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@AllArgsConstructor(access = AccessLevel.PUBLIC)
public final class QuickQuestion extends AuditMetadata {
  @Id @UUIDv7Sequence private String id;

  private Integer sortOrder;
  private String questionText;
  private Boolean enabled;
}
