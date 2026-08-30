package org.acm.ca.interfaces.http.controller;

import jakarta.validation.Valid;
import java.util.concurrent.Executor;
import org.acm.ca.application.port.in.AgentUseCase;
import org.acm.ca.interfaces.http.mapper.AgentRequestMapper;
import org.acm.ca.interfaces.http.request.AgentReplyRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Exposes the agent-native SSE reply endpoint. Streams on a separate thread so the request thread
 * returns immediately. The path follows the gateway convention: {@code /api/agent/**} is stripped
 * to {@code /agent/**} by the gateway's {@code StripPrefix=2} filter.
 */
@RestController
public class AgentController {

  private final AgentUseCase agentUseCase;
  private final AgentRequestMapper mapper;
  private final Executor executor;

  public AgentController(
      AgentUseCase agentUseCase,
      AgentRequestMapper mapper,
      @Qualifier("sseExecutor") Executor executor) {
    this.agentUseCase = agentUseCase;
    this.mapper = mapper;
    this.executor = executor;
  }

  @PostMapping(value = "/agent/reply", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  SseEmitter reply(@Valid @RequestBody AgentReplyRequest request) {
    SseEmitter emitter = new SseEmitter(30_000L);
    executor.execute(
        () -> {
          try {
            agentUseCase.streamReply(mapper.toCommand(request), new SseReplyStream(emitter));
          } catch (Exception e) {
            emitter.completeWithError(e);
          }
        });
    return emitter;
  }
}
