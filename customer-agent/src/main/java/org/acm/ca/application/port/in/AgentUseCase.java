package org.acm.ca.application.port.in;

/**
 * Inbound use case port. Streams reply events to {@link ReplyStream} as LLM tokens are produced.
 */
public interface AgentUseCase {

  void streamReply(GenerateReplyCommand command, ReplyStream stream);
}