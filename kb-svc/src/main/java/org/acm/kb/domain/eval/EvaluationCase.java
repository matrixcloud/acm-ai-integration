package org.acm.kb.domain.eval;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.acm.kb.domain.shared.InvalidRequestException;

/**
 * A single test question within an {@link EvaluationSuite}.
 *
 * <p>Each case has a query and an optional expected answer. The three evaluation metrics are
 * reference-free (LLM-as-judge), so {@code expectedAnswer} is not strictly required.
 */
@Entity
@Table(name = "evaluation_cases")
@EqualsAndHashCode(callSuper = false)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public final class EvaluationCase {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long suiteId;
  private int seqNo;
  private String query;
  private String expectedAnswer;

  /**
   * Factory for a new evaluation case.
   *
   * @param suiteId owning suite id
   * @param seqNo sequence number within the suite
   * @param query test question text (non-blank)
   * @param expectedAnswer optional reference answer
   * @return a new {@link EvaluationCase}
   */
  public static EvaluationCase of(Long suiteId, int seqNo, String query, String expectedAnswer) {
    String trimmedQuery = trimQuery(query);
    EvaluationCase caseEntity = new EvaluationCase();
    caseEntity.suiteId = suiteId;
    caseEntity.seqNo = seqNo;
    caseEntity.query = trimmedQuery;
    caseEntity.expectedAnswer = expectedAnswer;
    return caseEntity;
  }

  private static String trimQuery(String query) {
    if (query == null) {
      throw new InvalidRequestException("Evaluation case query must not be null");
    }
    String trimmed = query.strip();
    if (trimmed.isEmpty()) {
      throw new InvalidRequestException("Evaluation case query must not be blank");
    }
    return trimmed;
  }
}
