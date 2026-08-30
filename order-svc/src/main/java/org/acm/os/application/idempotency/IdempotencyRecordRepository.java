package org.acm.os.application.idempotency;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository port for {@link IdempotencyRecord}.
 *
 * <p>Lookups are scoped by (operation, idempotencyKey) to detect replay of a previously completed
 * request.
 */
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {
  Optional<IdempotencyRecord> findByOperationAndIdempotencyKey(
      String operation, String idempotencyKey);
}
