package org.acm.ca.application.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RuleRouterTest {

  private static RuleRouter routerOf(List<ReplyRule> rules) {
    return new RuleRouter(new ReplyRulesConfig("default", "KB-1", 5, rules));
  }

  @Test
  void matchesRuleByKeyword() {
    RuleRouter router = routerOf(List.of(new ReplyRule("REFUND", "退款,退货", "refund", 8)));
    assertThat(router.match("我要退款").map(ReplyRule::name)).contains("REFUND");
  }

  @Test
  void returnsHighestPriorityWhenMultipleKeywordsMatch() {
    RuleRouter router =
        routerOf(
            List.of(
                new ReplyRule("REFUND", "退款", "refund", 8),
                new ReplyRule("ORDER_STATUS", "订单", "order", 10)));
    assertThat(router.match("退款订单").map(ReplyRule::name)).contains("ORDER_STATUS");
  }

  @Test
  void returnsEmptyWhenNoKeywordMatches() {
    RuleRouter router = routerOf(List.of(new ReplyRule("REFUND", "退款", "refund", 8)));
    assertThat(router.match("你好")).isEmpty();
  }

  @Test
  void trimsKeywordWhitespace() {
    RuleRouter router = routerOf(List.of(new ReplyRule("REFUND", "退款 , 退货", "refund", 8)));
    assertThat(router.match("我要退货")).isPresent();
  }

  @Test
  void ignoresEmptyKeywordAfterSplit() {
    RuleRouter router = routerOf(List.of(new ReplyRule("REFUND", "退款,, ", "refund", 8)));
    assertThat(router.match("我要退款")).isPresent();
    assertThat(router.match("你好")).isEmpty();
  }

  @Test
  void handlesEmptyRules() {
    RuleRouter router = routerOf(List.of());
    assertThat(router.match("退款")).isEmpty();
  }
}