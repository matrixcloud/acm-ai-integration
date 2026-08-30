package org.acm.kb.domain.eval;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.acm.common.persistence.UUIDv7Sequence;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Per-case scoring detail for a single question within an {@link EvaluationRun}.
 *
 * <p>Holds the query, generated answer, and the three metric scores (context relevancy,
 * faithfulness, answer relevancy).
 */
@Entity
@Table(name = "evaluation_run_details")
@EntityListeners(AuditingEntityListener.class)
@EqualsAndHashCode(callSuper = false)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public final class EvaluationRunDetail {
  @Id @UUIDv7Sequence private String id;

  private String runId;
  private String query;
  private String generatedAnswer;
  private double contextRelevancyScore;
  private double faithfulnessScore;
  private double answerRelevancyScore;

  @CreatedDate private LocalDateTime createdAt;

  /**
   * Factory for a new run detail.
   *
   * @param runId owning run id
   * @param query test question
   * @param generatedAnswer answer produced by the evaluation pipeline
   * @param contextRelevancyScore context-relevancy score (0/1)
   * @param faithfulnessScore faithfulness score (0/1)
   * @param answerRelevancyScore answer-relevancy score (0/1)
   * @return a new {@link EvaluationRunDetail}
   */
  public static EvaluationRunDetail of(
      String runId,
      String query,
      String generatedAnswer,
      double contextRelevancyScore,
      double faithfulnessScore,
      double answerRelevancyScore) {
    EvaluationRunDetail detail = new EvaluationRunDetail();
    detail.runId = runId;
    detail.query = query;
    detail.generatedAnswer = generatedAnswer;
    detail.contextRelevancyScore = contextRelevancyScore;
    detail.faithfulnessScore = faithfulnessScore;
    detail.answerRelevancyScore = answerRelevancyScore;
    return detail;
  }
}
