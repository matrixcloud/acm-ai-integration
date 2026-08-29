package org.acm.kb.domain.eval;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationCaseRepository extends JpaRepository<EvaluationCase, Long> {

  List<EvaluationCase> findBySuiteId(Long suiteId);

  long countBySuiteId(Long suiteId);
}
