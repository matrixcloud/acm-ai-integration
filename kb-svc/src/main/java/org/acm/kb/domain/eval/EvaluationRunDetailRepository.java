package org.acm.kb.domain.eval;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationRunDetailRepository extends JpaRepository<EvaluationRunDetail, String> {

  List<EvaluationRunDetail> findByRunId(String runId);
}
