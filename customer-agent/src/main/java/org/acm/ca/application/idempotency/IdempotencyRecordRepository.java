package org.acm.ca.application.idempotency;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {
  Optional<IdempotencyRecord> findByOperationAndIdempotencyKey(
      String operation, String idempotencyKey);
}
