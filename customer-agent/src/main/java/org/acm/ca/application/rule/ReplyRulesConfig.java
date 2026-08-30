package org.acm.ca.application.rule;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds {@code customer.agent.*} from {@code application.yml}. */
@ConfigurationProperties(prefix = "customer.agent")
public record ReplyRulesConfig(String defaultSystemPrompt, int kbTopK, List<ReplyRule> rules) {

  public ReplyRulesConfig {
    if (rules == null) {
      rules = List.of();
    }
  }
}
