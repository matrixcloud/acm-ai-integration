package org.acm.ca.interfaces.http.controller;

import java.io.IOException;
import java.util.Map;
import org.acm.ca.application.port.in.ReplyStream;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Adapts {@link SseEmitter} to the transport-neutral {@link ReplyStream}. */
public class SseReplyStream implements ReplyStream {

  private final SseEmitter emitter;

  public SseReplyStream(SseEmitter emitter) {
    this.emitter = emitter;
  }

  @Override
  public void emitChunk(String token) {
    send(SseEmitter.event().name("chunk").data(token));
  }

  @Override
  public void emitDone(String fullContent) {
    send(SseEmitter.event().name("done").data(Map.of("content", fullContent)));
    emitter.complete();
  }

  @Override
  public void emitError(String code, String detail) {
    send(SseEmitter.event().name("error").data(Map.of("code", code, "detail", detail)));
    emitter.complete();
  }

  private void send(SseEmitter.SseEventBuilder event) {
    try {
      emitter.send(event);
    } catch (IOException e) {
      emitter.completeWithError(e);
    }
  }
}
