package org.acm.ca.application.port.in;

/**
 * Transport-neutral streaming callback. Binds neither Reactor {@code Flux} nor {@code SseEmitter},
 * keeping the application port free of transport types.
 */
public interface ReplyStream {

  void emitChunk(String token);

  void emitDone(String fullContent);

  void emitError(String code, String detail);
}
