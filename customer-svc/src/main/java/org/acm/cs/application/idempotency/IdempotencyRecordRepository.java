package org.acm.cs.application.idempotency;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {
  Optional<IdempotencyRecord> findByOperationAndIdempotencyKey(
      String operation, String idempotencyKey);
}
