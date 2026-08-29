package org.acm.kb.domain.eval;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.acm.kb.domain.shared.AuditMetadata;

/**
 * An evaluation run aggregate root.
 *
 * <p>Records the execution of a batch evaluation against a knowledge base using a test suite.
 * Holds the aggregated metrics and lifecycle timestamps; per-case scores live in {@link
 * EvaluationRunDetail}.
 */
@Entity
@Table(name = "evaluation_runs")
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public final class EvaluationRun extends AuditMetadata {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String runNo;
  private String kbNo;
  private Long suiteId;
  @Enumerated(EnumType.STRING)
  private EvaluationRunStatus status;
  private int topK;
  private double contextRelevancyAvg;
  private double faithfulnessAvg;
  private double answerRelevancyAvg;
  private double contextRelevancyPassRate;
  private double faithfulnessPassRate;
  private double answerRelevancyPassRate;
  private LocalDateTime startedAt;
  private LocalDateTime finishedAt;

  /**
   * Factory for a new evaluation run in {@link EvaluationRunStatus#RUNNING}.
   *
   * @param kbNo knowledge base number being evaluated
   * @param suiteId test suite id
   * @param topK retrieval top-K parameter
   * @return a new {@link EvaluationRun}
   */
  public static EvaluationRun create(String kbNo, Long suiteId, int topK) {
    EvaluationRun run = new EvaluationRun();
    run.runNo = generateRunNo();
    run.kbNo = kbNo;
    run.suiteId = suiteId;
    run.status = EvaluationRunStatus.RUNNING;
    run.topK = topK;
    run.startedAt = LocalDateTime.now();
    return run;
  }

  /** Marks the run as completed with the aggregated metrics. */
  public void markCompleted(EvaluationMetrics metrics) {
    this.status = EvaluationRunStatus.COMPLETED;
    this.contextRelevancyAvg = metrics.contextRelevancyAvg();
    this.faithfulnessAvg = metrics.faithfulnessAvg();
    this.answerRelevancyAvg = metrics.answerRelevancyAvg();
    this.contextRelevancyPassRate = metrics.contextRelevancyPassRate();
    this.faithfulnessPassRate = metrics.faithfulnessPassRate();
    this.answerRelevancyPassRate = metrics.answerRelevancyPassRate();
    this.finishedAt = LocalDateTime.now();
  }

  /** Marks the run as failed. */
  public void markFailed() {
    this.status = EvaluationRunStatus.FAILED;
    this.finishedAt = LocalDateTime.now();
  }

  private static final DateTimeFormatter TIMESTAMP_FORMAT =
      DateTimeFormatter.ofPattern("yyMMddHHmmss");

  private static String generateRunNo() {
    String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
    int random = ThreadLocalRandom.current().nextInt(0, 1_000_000);
    return "EVAL-RUN-" + timestamp + String.format("%06d", random);
  }
}
