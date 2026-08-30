package org.acm.os.application.idempotency;

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
import org.acm.os.domain.shared.AuditMetadata;

/**
 * Persisted record of an idempotent operation outcome.
 *
 * <p>Keyed by (operation, idempotencyKey) so that a client re-sending the same key for the same
 * operation receives the previously stored result instead of re-executing the operation.
 *
 * <p>{@code status} is the record's own lifecycle (PENDING → COMPLETED) and must not carry any
 * other meaning. {@code responseStatus} is retained for schema compatibility but unused — replay
 * reads only {@code responseBody}.
 */
@Entity
@Table(
    name = "idempotency_records",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_idempotency_key",
            columnNames = {"operation", "idempotency_key"}))
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

  /**
   * Marks the operation as finished and stores the payload to replay.
   *
   * @param responseBody serialized result to replay on retry
   * @param responseStatus unused (retained for schema compat); replay reads only the body
   */
  public void complete(String responseBody, String responseStatus) {
    this.responseBody = responseBody;
    this.responseStatus = responseStatus;
    this.status = STATUS_COMPLETED;
  }
}
