package org.acm.ca.application.idempotency;

import jakarta.persistence.Entity;
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
import org.acm.ca.domain.shared.AuditMetadata;

@Entity
@Table(
    name = "idempotency_records",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_idempotency_key", columnNames = {"operation", "idempotency_key"}))
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@AllArgsConstructor(access = AccessLevel.PUBLIC)
public final class IdempotencyRecord extends AuditMetadata {
  public static final String STATUS_PENDING = "PENDING";
  public static final String STATUS_COMPLETED = "COMPLETED";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private String operation;
  private String idempotencyKey;
  private String requestHash;
  private String responseBody;
  private String responseStatus;
  private String status;

  public void complete(String responseBody, String responseStatus) {
    this.responseBody = responseBody;
    this.responseStatus = responseStatus;
    this.status = STATUS_COMPLETED;
  }
}
