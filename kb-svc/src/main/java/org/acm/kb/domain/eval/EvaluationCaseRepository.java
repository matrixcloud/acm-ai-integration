package org.acm.kb.domain.eval;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationCaseRepository extends JpaRepository<EvaluationCase, String> {

  List<EvaluationCase> findBySuiteId(String suiteId);

  long countBySuiteId(String suiteId);
}
