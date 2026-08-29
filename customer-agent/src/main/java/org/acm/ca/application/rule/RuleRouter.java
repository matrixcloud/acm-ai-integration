package org.acm.ca.application.rule;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Keyword-containment router. Rules are ordered by descending priority and matched in memory; the
 * first rule whose any keyword is contained in the customer message wins.
 */
@Component
public class RuleRouter {

  private final List<ReplyRule> rules;

  public RuleRouter(ReplyRulesConfig config) {
    this.rules =
        config.rules().stream()
            .sorted(Comparator.comparingInt(ReplyRule::priority).reversed())
            .toList();
  }

  public Optional<ReplyRule> match(String customerMessage) {
    for (ReplyRule rule : rules) {
      for (String keyword : rule.keywords().split(",")) {
        String trimmed = keyword.strip();
        if (!trimmed.isEmpty() && customerMessage.contains(trimmed)) {
          return Optional.of(rule);
        }
      }
    }
    return Optional.empty();
  }
}