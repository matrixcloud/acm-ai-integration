package org.acm.ca.application.port.in;

/**
 * Transport-neutral streaming callback for the send-message use case. {@code emitDone} carries the
 * persisted {@link ConversationUseCase.MessageThread} and must only fire after the surrounding
 * transaction has committed; after {@code emitError} no further events are emitted.
 *
 * <p>Implementations may deliver events best-effort: transport failures (e.g. client disconnect)
 * disable further emission without aborting the use case, leaving the business outcome — commit
 * or rollback driven solely by application errors — deterministic.
 */
public interface ConversationStream {

  void emitChunk(String token);

  void emitDone(ConversationUseCase.MessageThread thread);

  void emitError(String code, String detail);
}
