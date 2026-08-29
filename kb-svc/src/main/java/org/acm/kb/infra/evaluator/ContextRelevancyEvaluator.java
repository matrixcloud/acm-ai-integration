package org.acm.kb.infra.evaluator;

import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.evaluation.Evaluator;

/**
 * Custom evaluator that judges whether retrieved context is relevant to the user's query.
 *
 * <p>Implements {@link Evaluator} and uses an LLM-as-judge prompt that returns {@code YES} or
 * {@code NO}. Spring AI has no built-in context-relevancy evaluator, so this fills the gap
 * alongside {@code FactCheckingEvaluator} (faithfulness) and {@code RelevancyEvaluator} (answer
 * relevancy).
 */
public class ContextRelevancyEvaluator implements Evaluator {

  private static final PromptTemplate PROMPT =
      new PromptTemplate(
          """
          你的任务是判断以下检索上下文是否与用户问题相关。
          只回答 YES 或 NO。YES 表示上下文包含回答问题所需的信息，NO 表示无关。

          用户问题：
          {query}

          检索上下文：
          {context}

          答案：
          """);

  private final ChatClient.Builder chatClientBuilder;

  /**
   * Constructs the evaluator with the judge {@link ChatClient.Builder}.
   *
   * @param chatClientBuilder builder for the LLM judge client
   */
  public ContextRelevancyEvaluator(ChatClient.Builder chatClientBuilder) {
    this.chatClientBuilder = chatClientBuilder;
  }

  @Override
  public EvaluationResponse evaluate(EvaluationRequest request) {
    String context = doGetSupportingData(request);
    String userMessage =
        PROMPT.render(Map.of("query", request.getUserText(), "context", context));
    String result =
        chatClientBuilder.build().prompt().user(userMessage).call().content();
    boolean pass = "yes".equalsIgnoreCase(result != null ? result.strip() : "");
    return new EvaluationResponse(pass, pass ? 1f : 0f, "", Map.of());
  }
}
