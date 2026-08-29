package org.acm.ca.application.service;

import java.util.List;
import org.acm.ca.application.port.in.AgentUseCase;
import org.acm.ca.application.port.in.GenerateReplyCommand;
import org.acm.ca.application.port.in.ReplyStream;
import org.acm.ca.application.port.out.AiAgentClient;
import org.acm.ca.domain.conversation.MessageRole;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * In-process adapter that binds the {@link AiAgentClient} outbound port to the agent use case,
 * making the real agent the default reply-generation implementation of the merged service.
 */
@Component
@ConditionalOnProperty(name = "customer.adapters.ai-agent", havingValue = "real", matchIfMissing = true)
public class InProcessAiAgentClient implements AiAgentClient {

  private final AgentUseCase agentUseCase;

  public InProcessAiAgentClient(AgentUseCase agentUseCase) {
    this.agentUseCase = agentUseCase;
  }

  @Override
  public void streamReply(ReplyRequest request, ReplyStream stream) {
    agentUseCase.streamReply(toCommand(request), stream);
  }

  private GenerateReplyCommand toCommand(ReplyRequest request) {
    List<GenerateReplyCommand.MessageContext> messages =
        request.recentMessages().stream()
            .map(
                m ->
                    new GenerateReplyCommand.MessageContext(
                        m.role().name(), m.content(), m.createdAt()))
            .toList();
    List<GenerateReplyCommand.OrderSummary> orders =
        request.recentOrders().stream()
            .map(
                o ->
                    new GenerateReplyCommand.OrderSummary(
                        o.orderNo(), o.status(), o.payableTotal(), o.currency(), o.createdAt()))
            .toList();
    return new GenerateReplyCommand(
        request.conversationNo(),
        request.customerId(),
        messages,
        orders,
        request.customerMessage());
  }
}
