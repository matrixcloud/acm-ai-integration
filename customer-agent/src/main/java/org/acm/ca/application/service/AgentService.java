package org.acm.ca.application.service;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.time.format.DateTimeFormatter;
import lombok.extern.slf4j.Slf4j;
import org.acm.ca.application.port.in.AgentUseCase;
import org.acm.ca.application.port.in.GenerateReplyCommand;
import org.acm.ca.application.port.in.ReplyStream;
import org.acm.ca.application.rule.ReplyRule;
import org.acm.ca.application.rule.ReplyRulesConfig;
import org.acm.ca.application.rule.RuleRouter;
import org.acm.ca.domain.shared.InvalidRequestException;
import org.acm.ca.infra.llm.KbSearchTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the hybrid paradigm: rule router for the fast path, ReAct tool-calling for the slow
 * path. LLM tokens are consumed from Spring AI's {@code Flux} internally and pushed through the
 * transport-neutral {@link ReplyStream}; the {@code Flux} never leaks to the caller.
 */
@Service
@Slf4j
public class AgentService implements AgentUseCase {

  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

  private final ChatClient chatClient;
  private final RuleRouter ruleRouter;
  private final KbSearchTool kbSearchTool;
  private final ReplyRulesConfig config;
  private final ObservationRegistry observationRegistry;

  public AgentService(
      ChatClient chatClient,
      RuleRouter ruleRouter,
      KbSearchTool kbSearchTool,
      ReplyRulesConfig config,
      ObservationRegistry observationRegistry) {
    this.chatClient = chatClient;
    this.ruleRouter = ruleRouter;
    this.kbSearchTool = kbSearchTool;
    this.config = config;
    this.observationRegistry = observationRegistry;
  }

  @Override
  public void streamReply(GenerateReplyCommand command, ReplyStream stream) {
    if (command.customerMessage() == null || command.customerMessage().isBlank()) {
      throw new InvalidRequestException("Customer message must not be blank");
    }

    var rule = ruleRouter.match(command.customerMessage());

    Observation observation =
        Observation.createNotStarted("agent.reply", observationRegistry)
            .lowCardinalityKeyValue("path", rule.isPresent() ? "fast" : "react")
            .lowCardinalityKeyValue("rule.matched", rule.isPresent() ? "true" : "false");
    rule.ifPresent(r -> observation.lowCardinalityKeyValue("rule.name", r.name()));
    if (command.conversationNo() != null) {
      observation.highCardinalityKeyValue("conversation_no", command.conversationNo());
    }
    observation.start();

    try (var scope = observation.openScope()) {
      String systemPrompt = rule.map(ReplyRule::systemPrompt).orElse(config.defaultSystemPrompt());
      String userMessage = buildUserMessage(command);

      var spec = chatClient.prompt().system(systemPrompt).user(userMessage);
      if (rule.isEmpty()) {
        spec = spec.tools(kbSearchTool);
      }

      StringBuilder fullReply = new StringBuilder();
      spec.stream()
          .content()
          .doOnNext(
              token -> {
                fullReply.append(token);
                stream.emitChunk(token);
              })
          .doOnComplete(
              () -> {
                if (fullReply.toString().isBlank()) {
                  log.warn(
                      "agent.reply.empty conversationNo={} path={}",
                      command.conversationNo(),
                      rule.isPresent() ? "fast" : "react");
                  stream.emitError("LLM_UNAVAILABLE", "LLM returned empty content");
                } else {
                  log.info(
                      "agent.reply.ok conversationNo={} path={} replyChars={}",
                      command.conversationNo(),
                      rule.isPresent() ? "fast" : "react",
                      fullReply.length());
                  stream.emitDone(fullReply.toString().strip());
                }
              })
          .doOnError(
              e -> {
                log.error(
                    "agent.reply.failed conversationNo={} path={}",
                    command.conversationNo(),
                    rule.isPresent() ? "fast" : "react",
                    e);
                stream.emitError("LLM_UNAVAILABLE", e.getMessage());
              })
          .blockLast();

      observation.stop();
    } catch (RuntimeException e) {
      observation.error(e);
      observation.stop();
      throw e;
    }
  }

  String buildUserMessage(GenerateReplyCommand command) {
    StringBuilder sb = new StringBuilder();

    if (command.recentMessages() != null && !command.recentMessages().isEmpty()) {
      sb.append("## 对话历史\n");
      for (var message : command.recentMessages()) {
        String role = "AGENT".equals(message.role()) ? "客服" : "客户";
        sb.append('[').append(role).append("] ");
        if (message.createdAt() != null) {
          sb.append(message.createdAt().format(TIME));
        }
        sb.append(": ").append(message.content()).append('\n');
      }
      sb.append('\n');
    }

    sb.append("## 订单信息\n");
    if (command.recentOrders() != null && !command.recentOrders().isEmpty()) {
      for (var order : command.recentOrders()) {
        sb.append("- 订单号: ")
            .append(order.orderNo())
            .append(" | 状态: ")
            .append(order.status())
            .append(" | 金额: ")
            .append(order.payableTotal())
            .append(' ')
            .append(order.currency())
            .append('\n');
      }
    } else {
      sb.append("（无订单信息）\n");
    }
    sb.append('\n');

    sb.append("## 客户消息\n").append(command.customerMessage());
    return sb.toString();
  }
}
