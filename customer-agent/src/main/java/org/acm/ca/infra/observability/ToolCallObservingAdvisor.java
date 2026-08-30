package org.acm.ca.infra.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.Generation;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Observes each iteration of the tool-calling loop. Runs inside {@code ToolCallingAdvisor} (order
 * {@code HIGHEST_PRECEDENCE + 300}) so it can count tool-call requests and final answers.
 */
@Component
public class ToolCallObservingAdvisor implements CallAdvisor, StreamAdvisor {

  private static final Logger logger = LoggerFactory.getLogger(ToolCallObservingAdvisor.class);

  private final MeterRegistry meterRegistry;

  public ToolCallObservingAdvisor(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  @Override
  public String getName() {
    return "ToolCallObservingAdvisor";
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 400;
  }

  @Override
  public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
    ChatClientResponse response = chain.nextCall(request);
    observe(response);
    return response;
  }

  @Override
  public Flux<ChatClientResponse> adviseStream(
      ChatClientRequest request, StreamAdvisorChain chain) {
    return chain.nextStream(request).doOnNext(this::observe);
  }

  private void observe(ChatClientResponse response) {
    var chatResponse = response.chatResponse();
    if (chatResponse != null && chatResponse.hasToolCalls()) {
      chatResponse.getResults().stream()
          .map(Generation::getOutput)
          .filter(AssistantMessage.class::isInstance)
          .map(AssistantMessage.class::cast)
          .flatMap(message -> message.getToolCalls().stream())
          .forEach(
              toolCall -> {
                meterRegistry
                    .counter("agent.tool.calls", "name", toolCall.name(), "status", "requested")
                    .increment();
                logger.info(
                    "tool call requested: name={} args={}", toolCall.name(), toolCall.arguments());
              });
    } else {
      meterRegistry.counter("agent.react.iterations", "outcome", "final").increment();
    }
  }
}
