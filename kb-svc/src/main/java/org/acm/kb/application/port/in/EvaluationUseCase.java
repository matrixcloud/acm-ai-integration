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

  /**
   * Creates a new evaluation test suite.
   *
   * @param name suite name
   * @return the created suite
   */
  EvaluationSuite createSuite(String name);

  /**
   * Lists all evaluation test suites with their case counts.
   *
   * @return all suite summaries
   */
  List<SuiteSummary> listSuites();

  /**
   * Adds a test case to a suite.
   *
   * @param suiteNo suite number
   * @param query test question
   * @param expectedAnswer optional reference answer
   * @return the updated suite summary with case count
   */
  SuiteSummary addCase(String suiteNo, String query, String expectedAnswer);

  /**
   * Starts a batch evaluation run.
   *
   * @param kbNo knowledge base number
   * @param suiteNo suite number
   * @param topK retrieval top-K
   * @return the evaluation run
   */
  EvaluationRun startRun(String kbNo, String suiteNo, int topK);

  /**
   * Retrieves an evaluation run with its per-case details.
   *
   * @param runNo run number
   * @return the run report
   */
  RunReport getRun(String runNo);

  /** A suite with its case count. */
  record SuiteSummary(EvaluationSuite suite, int caseCount) {}

  /** An evaluation run with its per-case details. */
  record RunReport(EvaluationRun run, List<EvaluationRunDetail> details) {}
}
