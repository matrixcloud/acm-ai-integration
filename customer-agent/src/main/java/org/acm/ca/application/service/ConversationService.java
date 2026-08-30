package org.acm.ca.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.acm.ca.application.port.in.AgentUseCase;
import org.acm.ca.application.port.in.ConversationStream;
import org.acm.ca.application.port.in.ConversationUseCase;
import org.acm.ca.application.port.in.GenerateReplyCommand;
import org.acm.ca.application.port.in.ReplyStream;
import org.acm.ca.application.port.in.command.CreateConversationCommand;
import org.acm.ca.application.port.in.command.SendMessageCommand;
import org.acm.ca.application.port.in.command.SubmitFeedbackCommand;
import org.acm.ca.application.port.in.query.SearchConversationQuery;
import org.acm.ca.application.port.out.AiAgentUnavailableException;
import org.acm.ca.application.port.out.OrderQueryClient;
import org.acm.ca.application.port.out.OrderQueryClient.OrderSummary;
import org.acm.ca.domain.conversation.Conversation;
import org.acm.ca.domain.conversation.ConversationNotFoundException;
import org.acm.ca.domain.conversation.ConversationRepository;
import org.acm.ca.domain.quickquestion.QuickQuestionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConversationService implements ConversationUseCase {
  private static final String DEFAULT_SORT_FIELD = "createdAt";
  private static final Sort.Direction DEFAULT_SORT_DIRECTION = Sort.Direction.DESC;
  static final String CREATE_OPERATION = "create-conversation";
  static final String SEND_MESSAGE_OPERATION = "send-message";
  static final String END_OPERATION = "end-conversation";
  static final String FEEDBACK_OPERATION = "submit-feedback";

  private final ConversationRepository conversationRepository;
  private final QuickQuestionRepository quickQuestionRepository;
  private final AgentUseCase agentUseCase;
  private final OrderQueryClient orderQueryClient;
  private final IdempotencyService idempotencyService;

  @Override
  public Conversation create(CreateConversationCommand command, String idempotencyKey) {
    IdempotencyService.IdempotentOperation<Conversation> operation =
        new IdempotencyService.IdempotentOperation<>(
            CREATE_OPERATION, idempotencyKey, command, Conversation.class);
    return idempotencyService.execute(operation, () -> createInternal(command));
  }

  private Conversation createInternal(CreateConversationCommand command) {
    Conversation conversation = Conversation.create(command.getCustomerId());
    return conversationRepository.saveAndFlush(conversation);
  }

  @Override
  public void streamMessage(
      SendMessageCommand command, String idempotencyKey, ConversationStream stream) {
    ForwardingAgentStream agentStream = new ForwardingAgentStream(stream);
    IdempotencyService.IdempotentOperation<MessageThread> operation =
        new IdempotencyService.IdempotentOperation<>(
            SEND_MESSAGE_OPERATION, idempotencyKey, command, MessageThread.class);
    MessageThread thread =
        idempotencyService.execute(operation, () -> streamMessageInternal(command, agentStream));
    stream.emitDone(thread);
  }

  private MessageThread streamMessageInternal(
      SendMessageCommand command, ForwardingAgentStream agentStream) {
    Conversation conversation = loadConversation(command.getConversationNo());
    conversation.addCustomerMessage(command.getContent());
    conversationRepository.saveAndFlush(conversation);

    List<GenerateReplyCommand.MessageContext> recentMessages =
        conversation.getMessages().stream()
            .limit(20)
            .map(
                m ->
                    new GenerateReplyCommand.MessageContext(
                        m.getRole().name(), m.getContent(), m.getCreatedAt()))
            .toList();
    List<OrderSummary> recentOrders =
        orderQueryClient.getRecentOrders(conversation.getCustomerId());
    List<GenerateReplyCommand.OrderSummary> commandOrders =
        recentOrders.stream()
            .map(
                order ->
                    new GenerateReplyCommand.OrderSummary(
                        order.orderNo(),
                        order.status(),
                        order.payableTotal(),
                        order.currency(),
                        order.createdAt()))
            .toList();

    GenerateReplyCommand replyCommand =
        new GenerateReplyCommand(
            conversation.getConversationNo(),
            conversation.getCustomerId(),
            recentMessages,
            commandOrders,
            command.getContent());
    agentUseCase.streamReply(replyCommand, agentStream);

    if (agentStream.fullContent() == null || agentStream.fullContent().isBlank()) {
      throw new AiAgentUnavailableException("Agent reply stream completed without content");
    }
    conversation.addAgentReply(agentStream.fullContent());
    conversationRepository.saveAndFlush(conversation);
    return new MessageThread(
        conversation.getConversationNo(), List.copyOf(conversation.getMessages()));
  }

  @Override
  public Conversation endConversation(String conversationNo, String idempotencyKey) {
    IdempotencyService.IdempotentOperation<Conversation> operation =
        new IdempotencyService.IdempotentOperation<>(
            END_OPERATION, idempotencyKey, conversationNo, Conversation.class);
    return idempotencyService.execute(operation, () -> endInternal(conversationNo));
  }

  private Conversation endInternal(String conversationNo) {
    Conversation conversation = loadConversation(conversationNo);
    conversation.end();
    return conversationRepository.saveAndFlush(conversation);
  }

  @Override
  public Conversation submitFeedback(SubmitFeedbackCommand command, String idempotencyKey) {
    IdempotencyService.IdempotentOperation<Conversation> operation =
        new IdempotencyService.IdempotentOperation<>(
            FEEDBACK_OPERATION, idempotencyKey, command, Conversation.class);
    return idempotencyService.execute(operation, () -> feedbackInternal(command));
  }

  private Conversation feedbackInternal(SubmitFeedbackCommand command) {
    Conversation conversation = loadConversation(command.getConversationNo());
    conversation.submitFeedback(command.getRating(), command.getComment());
    return conversationRepository.saveAndFlush(conversation);
  }

  @Override
  @Transactional(readOnly = true)
  public Conversation findByConversationNo(String conversationNo) {
    return loadConversation(conversationNo);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<Conversation> search(SearchConversationQuery query) {
    PageRequest pageRequest =
        PageRequest.of(
            query.getPage() - 1,
            query.getSize(),
            Sort.by(DEFAULT_SORT_DIRECTION, DEFAULT_SORT_FIELD));
    return query.getStatus() == null
        ? conversationRepository.findByCustomerId(query.getCustomerId(), pageRequest)
        : conversationRepository.findByCustomerIdAndStatus(
            query.getCustomerId(), query.getStatus(), pageRequest);
  }

  @Override
  @Transactional(readOnly = true)
  public List<QuickQuestionItem> listQuickQuestions() {
    return quickQuestionRepository.findByEnabledTrueOrderBySortOrderAsc().stream()
        .map(qq -> new QuickQuestionItem(qq.getId(), qq.getSortOrder(), qq.getQuestionText()))
        .toList();
  }

  private Conversation loadConversation(String conversationNo) {
    return conversationRepository
        .findByConversationNo(conversationNo)
        .orElseThrow(
            () ->
                new ConversationNotFoundException(
                    "Conversation '%s' not found".formatted(conversationNo)));
  }

  /**
   * Forwards agent tokens to the business stream while capturing the aggregated reply. An agent
   * stream error is converted to a thrown exception so the surrounding idempotent transaction rolls
   * back; the agent's own error code (e.g. {@code LLM_UNAVAILABLE}) is preserved so the SSE {@code
   * error} event reports it verbatim instead of the port's transport-level code.
   */
  private static final class ForwardingAgentStream implements ReplyStream {

    private final ConversationStream out;
    private String fullContent;

    private ForwardingAgentStream(ConversationStream out) {
      this.out = out;
    }

    private String fullContent() {
      return fullContent;
    }

    @Override
    public void emitChunk(String token) {
      out.emitChunk(token);
    }

    @Override
    public void emitDone(String content) {
      this.fullContent = content;
    }

    @Override
    public void emitError(String code, String detail) {
      throw new AgentStreamErrorException(code, detail);
    }
  }

  /** Preserves the agent stream's error code while keeping the port's exception type. */
  private static final class AgentStreamErrorException extends AiAgentUnavailableException {

    private final String streamCode;

    private AgentStreamErrorException(String streamCode, String detail) {
      super(detail);
      this.streamCode = streamCode;
    }

    @Override
    public String code() {
      return streamCode;
    }
  }
}
