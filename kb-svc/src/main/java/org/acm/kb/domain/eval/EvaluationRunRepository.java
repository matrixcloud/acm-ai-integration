package org.acm.kb.domain.eval;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationRunRepository extends JpaRepository<EvaluationRun, Long> {

  Optional<EvaluationRun> findByRunNo(String runNo);
}
