package org.acm.kb.domain.eval;

/**
 * Execution states of an evaluation run.
 *
 * <p>{@link #RUNNING} is the initial state when a run starts; {@link #COMPLETED} marks a fully
 * scored run; {@link #FAILED} marks a run that aborted on retrieval or LLM error.
 */
public enum EvaluationRunStatus {
  RUNNING,
  COMPLETED,
  FAILED
}
