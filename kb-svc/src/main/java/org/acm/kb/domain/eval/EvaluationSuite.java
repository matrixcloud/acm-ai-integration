package org.acm.kb.domain.eval;

import jakarta.persistence.Entity;
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
import org.acm.kb.domain.shared.InvalidRequestException;

/**
 * An evaluation test suite aggregate root.
 *
 * <p>Contains a set of {@link EvaluationCase} test questions. A suite is the benchmark against
 * which a knowledge base's retrieval quality is measured.
 */
@Entity
@Table(name = "evaluation_suites")
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public final class EvaluationSuite extends AuditMetadata {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String suiteNo;
  private String name;

  /**
   * Factory for a new evaluation suite.
   *
   * @param name suite name (non-blank)
   * @return a new {@link EvaluationSuite}
   */
  public static EvaluationSuite create(String name) {
    String trimmed = trimName(name);
    EvaluationSuite suite = new EvaluationSuite();
    suite.suiteNo = generateSuiteNo();
    suite.name = trimmed;
    return suite;
  }

  private static String trimName(String name) {
    if (name == null) {
      throw new InvalidRequestException("Evaluation suite name must not be null");
    }
    String trimmed = name.strip();
    if (trimmed.isEmpty()) {
      throw new InvalidRequestException("Evaluation suite name must not be blank");
    }
    return trimmed;
  }

  private static final DateTimeFormatter TIMESTAMP_FORMAT =
      DateTimeFormatter.ofPattern("yyMMddHHmmss");

  private static String generateSuiteNo() {
    String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
    int random = ThreadLocalRandom.current().nextInt(0, 1_000_000);
    return "EVAL-SUITE-" + timestamp + String.format("%06d", random);
  }
}
