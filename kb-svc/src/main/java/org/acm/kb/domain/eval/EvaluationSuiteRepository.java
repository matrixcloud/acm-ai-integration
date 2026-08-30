package org.acm.kb.domain.eval;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationSuiteRepository extends JpaRepository<EvaluationSuite, String> {

  Optional<EvaluationSuite> findBySuiteNo(String suiteNo);
}
