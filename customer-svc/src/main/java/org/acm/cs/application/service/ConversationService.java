package org.acm.cs.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.acm.cs.application.port.in.ConversationUseCase;
import org.acm.cs.application.port.in.command.CreateConversationCommand;
import org.acm.cs.application.port.in.command.SendMessageCommand;
import org.acm.cs.application.port.in.command.SubmitFeedbackCommand;
import org.acm.cs.application.port.in.query.SearchConversationQuery;
import org.acm.cs.application.port.out.AiAgentClient;
import org.acm.cs.application.port.out.AiAgentClient.AgentReply;
import org.acm.cs.application.port.out.AiAgentClient.MessageContext;
import org.acm.cs.application.port.out.AiAgentClient.ReplyRequest;
import org.acm.cs.application.port.out.OrderQueryClient;
import org.acm.cs.application.port.out.OrderQueryClient.OrderSummary;
import org.acm.cs.domain.conversation.Conversation;
import org.acm.cs.domain.conversation.ConversationNotFoundException;
import org.acm.cs.domain.conversation.ConversationRepository;
import org.acm.cs.domain.quickquestion.QuickQuestionRepository;
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
  private final AiAgentClient aiAgentClient;
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
  public MessageThread sendMessage(SendMessageCommand command, String idempotencyKey) {
    IdempotencyService.IdempotentOperation<MessageThread> operation =
        new IdempotencyService.IdempotentOperation<>(
            SEND_MESSAGE_OPERATION, idempotencyKey, command, MessageThread.class);
    return idempotencyService.execute(operation, () -> sendMessageInternal(command));
  }

  private MessageThread sendMessageInternal(SendMessageCommand command) {
    Conversation conversation = loadConversation(command.getConversationNo());
    conversation.addCustomerMessage(command.getContent());
    conversationRepository.saveAndFlush(conversation);

    List<MessageContext> recentMessages =
        conversation.getMessages().stream()
            .limit(20)
            .map(m -> new MessageContext(m.getRole(), m.getContent(), m.getCreatedAt()))
            .toList();
    List<OrderSummary> recentOrders = orderQueryClient.getRecentOrders(conversation.getCustomerId());

    ReplyRequest replyRequest =
        new ReplyRequest(
            conversation.getConversationNo(),
            conversation.getCustomerId(),
            recentMessages,
            recentOrders,
            command.getContent());
    AgentReply reply = aiAgentClient.generate(replyRequest);

    conversation.addAgentReply(reply.content());
    conversationRepository.saveAndFlush(conversation);
    return new MessageThread(conversation.getConversationNo(), List.copyOf(conversation.getMessages()));
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
        PageRequest.of(query.getPage() - 1, query.getSize(), Sort.by(DEFAULT_SORT_DIRECTION, DEFAULT_SORT_FIELD));
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
}
