package org.acm.kb.domain.eval;

/**
 * Aggregated metrics for an evaluation run across all test cases.
 *
 * <p>Each indicator is reference-free (LLM-as-judge) and binary (0/1); {@code avgScore} equals
 * {@code passRate} for the current binary scoring but both fields are retained for future
 * continuous-score support.
 *
 * @param contextRelevancyAvg average context-relevancy score
 * @param faithfulnessAvg average faithfulness score
 * @param answerRelevancyAvg average answer-relevancy score
 * @param contextRelevancyPassRate context-relevancy pass rate
 * @param faithfulnessPassRate faithfulness pass rate
 * @param answerRelevancyPassRate answer-relevancy pass rate
 */
public record EvaluationMetrics(
    double contextRelevancyAvg,
    double faithfulnessAvg,
    double answerRelevancyAvg,
    double contextRelevancyPassRate,
    double faithfulnessPassRate,
    double answerRelevancyPassRate) {}
