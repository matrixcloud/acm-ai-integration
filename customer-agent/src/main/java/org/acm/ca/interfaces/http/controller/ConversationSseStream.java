package org.acm.ca.interfaces.http.controller;

import java.io.IOException;
import java.util.Map;
import org.acm.ca.application.port.in.ConversationStream;
import org.acm.ca.application.port.in.ConversationUseCase.MessageThread;
import org.acm.ca.interfaces.http.mapper.ConversationResponseMapper;
import org.acm.ca.interfaces.http.response.MessageThreadResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Adapts {@link SseEmitter} to the transport-neutral {@link ConversationStream}.
 *
 * <p>Delivery is best-effort by explicit decision: once a send fails (client disconnect, emitter
 * already completed by timeout or container), the adapter stops emitting for good so the
 * surrounding idempotent transaction finishes deterministically instead of being cancelled
 * mid-flight. The persisted result stays retrievable through idempotent replay.
 */
public class ConversationSseStream implements ConversationStream {

  private final SseEmitter emitter;
  private final ConversationResponseMapper responseMapper;
  private boolean dead;

  public ConversationSseStream(SseEmitter emitter, ConversationResponseMapper responseMapper) {
    this.emitter = emitter;
    this.responseMapper = responseMapper;
  }

  @Override
  public void emitChunk(String token) {
    send(SseEmitter.event().name("chunk").data(token));
  }

  @Override
  public void emitDone(MessageThread thread) {
    MessageThreadResponse response = responseMapper.toThreadResponse(thread);
    send(SseEmitter.event().name("done").data(response));
    complete();
  }

  @Override
  public void emitError(String code, String detail) {
    send(SseEmitter.event().name("error").data(Map.of("code", code, "detail", detail)));
    complete();
  }

  private void send(SseEmitter.SseEventBuilder event) {
    if (dead) {
      return;
    }
    try {
      emitter.send(event);
    } catch (IOException | IllegalStateException e) {
      // The client is gone or the container completed the emitter; further sends are pointless
      // and would nondeterministically abort the business transaction. Mark dead and stop.
      dead = true;
      complete();
    }
  }

  private void complete() {
    if (dead) {
      return;
    }
    try {
      emitter.complete();
    } catch (RuntimeException e) {
      // Already completed by the servlet container (timeout/error path); nothing left to do.
      dead = true;
    }
  }
}
