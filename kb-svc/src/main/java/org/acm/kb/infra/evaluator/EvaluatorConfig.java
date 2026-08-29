package org.acm.kb.infra.evaluator;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.evaluation.FactCheckingEvaluator;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.ai.evaluation.Evaluator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the three LLM-as-judge evaluators used by the RAG evaluation pipeline.
 *
 * <p>{@code faithfulnessEvaluator} and {@code answerRelevancyEvaluator} are Spring AI built-ins;
 * {@code contextRelevancyEvaluator} is the custom {@link ContextRelevancyEvaluator}. Application
 * services depend only on the {@link Evaluator} abstraction.
 */
@Configuration
public class EvaluatorConfig {

  @Bean
  public Evaluator contextRelevancyEvaluator(ChatClient.Builder chatClientBuilder) {
    return new ContextRelevancyEvaluator(chatClientBuilder);
  }

  @Bean
  public Evaluator faithfulnessEvaluator(ChatClient.Builder chatClientBuilder) {
    return FactCheckingEvaluator.builder(chatClientBuilder).build();
  }

  @Bean
  public Evaluator answerRelevancyEvaluator(ChatClient.Builder chatClientBuilder) {
    return new RelevancyEvaluator(chatClientBuilder);
  }
}
