package org.acm.kb.application.port.in;

import java.util.List;
import org.acm.kb.domain.eval.EvaluationRun;
import org.acm.kb.domain.eval.EvaluationRunDetail;
import org.acm.kb.domain.eval.EvaluationSuite;

/**
 * Application port for RAG evaluation use cases.
 *
 * <p>Covers test-suite management and batch evaluation runs.
 */
public interface EvaluationUseCase {

  EvaluationSuite createSuite(String name);

  List<SuiteSummary> listSuites();

  SuiteSummary addCase(String suiteNo, String query, String expectedAnswer);

  EvaluationRun startRun(String kbNo, String suiteNo, int topK);

  RunReport getRun(String runNo);

  record SuiteSummary(EvaluationSuite suite, int caseCount) {}

  record RunReport(EvaluationRun run, List<EvaluationRunDetail> details) {}
}
