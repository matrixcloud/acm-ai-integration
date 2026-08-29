package org.acm.cs.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.acm.cs.application.port.in.ConversationUseCase.MessageThread;
import org.acm.cs.application.port.in.ConversationUseCase.QuickQuestionItem;
import org.acm.cs.application.port.in.command.CreateConversationCommand;
import org.acm.cs.application.port.in.command.SendMessageCommand;
import org.acm.cs.application.port.in.command.SubmitFeedbackCommand;
import org.acm.cs.application.port.in.query.SearchConversationQuery;
import org.acm.cs.application.port.out.AiAgentClient;
import org.acm.cs.application.port.out.AiAgentClient.AgentReply;
import org.acm.cs.application.port.out.AiAgentClient.ReplyRequest;
import org.acm.cs.application.port.out.OrderQueryClient;
import org.acm.cs.domain.conversation.Conversation;
import org.acm.cs.domain.conversation.ConversationNotActiveException;
import org.acm.cs.domain.conversation.ConversationNotFoundException;
import org.acm.cs.domain.conversation.ConversationRepository;
import org.acm.cs.domain.conversation.ConversationStatus;
import org.acm.cs.domain.conversation.FeedbackRating;
import org.acm.cs.domain.conversation.MessageRole;
import org.acm.cs.domain.quickquestion.QuickQuestion;
import org.acm.cs.domain.quickquestion.QuickQuestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

  @Mock private ConversationRepository conversationRepository;
  @Mock private QuickQuestionRepository quickQuestionRepository;
  @Mock private AiAgentClient aiAgentClient;
  @Mock private OrderQueryClient orderQueryClient;
  @Mock private IdempotencyService idempotencyService;

  @Captor private ArgumentCaptor<Conversation> conversationCaptor;
  @Captor private ArgumentCaptor<PageRequest> pageRequestCaptor;

  private ConversationService service;

  @BeforeEach
  void setUp() {
    service =
        new ConversationService(
            conversationRepository,
            quickQuestionRepository,
            aiAgentClient,
            orderQueryClient,
            idempotencyService);
    lenient()
        .when(idempotencyService.execute(any(), any()))
        .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
  }

  @Test
  void createPassesIdempotentOperationAndPersistsNewConversation() {
    CreateConversationCommand command = new CreateConversationCommand();
    command.setCustomerId("customer-1");
    when(conversationRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    @SuppressWarnings("unchecked")
    ArgumentCaptor<IdempotencyService.IdempotentOperation<Conversation>> operationCaptor =
        ArgumentCaptor.forClass(IdempotencyService.IdempotentOperation.class);

    Conversation result = service.create(command, "key-1");

    verify(idempotencyService).execute(operationCaptor.capture(), any());
    IdempotencyService.IdempotentOperation<Conversation> operation = operationCaptor.getValue();
    assertThat(operation.operation()).isEqualTo(ConversationService.CREATE_OPERATION);
    assertThat(operation.idempotencyKey()).isEqualTo("key-1");
    assertThat(operation.request()).isSameAs(command);
    assertThat(operation.responseType()).isEqualTo(Conversation.class);

    verify(conversationRepository).saveAndFlush(conversationCaptor.capture());
    Conversation saved = conversationCaptor.getValue();
    assertThat(saved.getCustomerId()).isEqualTo("customer-1");
    assertThat(saved.getStatus()).isEqualTo(ConversationStatus.ACTIVE);
    assertThat(saved.getConversationNo()).isNotBlank();
    assertThat(result).isSameAs(saved);
  }

  @Test
  void sendMessagePassesIdempotentOperationAndGeneratesReply() {
    Conversation conversation = Conversation.create("customer-1");
    String conversationNo = conversation.getConversationNo();
    SendMessageCommand command = new SendMessageCommand();
    command.setConversationNo(conversationNo);
    command.setContent("Hello");
    when(conversationRepository.findByConversationNo(conversationNo))
        .thenReturn(Optional.of(conversation));
    when(conversationRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    when(orderQueryClient.getRecentOrders("customer-1")).thenReturn(List.of());
    when(aiAgentClient.generate(any())).thenReturn(new AgentReply("Hi there!"));
    @SuppressWarnings("unchecked")
    ArgumentCaptor<IdempotencyService.IdempotentOperation<MessageThread>> operationCaptor =
        ArgumentCaptor.forClass(IdempotencyService.IdempotentOperation.class);

    MessageThread result = service.sendMessage(command, "key-1");

    verify(idempotencyService).execute(operationCaptor.capture(), any());
    IdempotencyService.IdempotentOperation<MessageThread> operation = operationCaptor.getValue();
    assertThat(operation.operation()).isEqualTo(ConversationService.SEND_MESSAGE_OPERATION);
    assertThat(operation.idempotencyKey()).isEqualTo("key-1");
    assertThat(operation.request()).isSameAs(command);
    assertThat(operation.responseType()).isEqualTo(MessageThread.class);

    ArgumentCaptor<ReplyRequest> replyCaptor = ArgumentCaptor.forClass(ReplyRequest.class);
    verify(aiAgentClient).generate(replyCaptor.capture());
    ReplyRequest request = replyCaptor.getValue();
    assertThat(request.conversationNo()).isEqualTo(conversationNo);
    assertThat(request.customerId()).isEqualTo("customer-1");
    assertThat(request.recentOrders()).isEmpty();
    assertThat(request.customerMessage()).isEqualTo("Hello");
    assertThat(request.recentMessages()).hasSize(1);
    assertThat(request.recentMessages().get(0).role()).isEqualTo(MessageRole.CUSTOMER);
    assertThat(request.recentMessages().get(0).content()).isEqualTo("Hello");

    assertThat(result.conversationNo()).isEqualTo(conversationNo);
    assertThat(result.messages()).hasSize(2);
    assertThat(result.messages().get(0).getRole()).isEqualTo(MessageRole.CUSTOMER);
    assertThat(result.messages().get(0).getContent()).isEqualTo("Hello");
    assertThat(result.messages().get(1).getRole()).isEqualTo(MessageRole.AGENT);
    assertThat(result.messages().get(1).getContent()).isEqualTo("Hi there!");

    verify(conversationRepository, times(2)).saveAndFlush(any());
  }

  @Test
  void sendMessageLimitsRecentMessagesToTwenty() {
    Conversation conversation = Conversation.create("customer-1");
    String conversationNo = conversation.getConversationNo();
    for (int i = 0; i < 10; i++) {
      conversation.addCustomerMessage("prior-customer-" + i);
      conversation.addAgentReply("prior-agent-" + i);
    }
    SendMessageCommand command = new SendMessageCommand();
    command.setConversationNo(conversationNo);
    command.setContent("new message");
    when(conversationRepository.findByConversationNo(conversationNo))
        .thenReturn(Optional.of(conversation));
    when(conversationRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    when(orderQueryClient.getRecentOrders("customer-1")).thenReturn(List.of());
    when(aiAgentClient.generate(any())).thenReturn(new AgentReply("reply"));

    service.sendMessage(command, "key-1");

    ArgumentCaptor<ReplyRequest> replyCaptor = ArgumentCaptor.forClass(ReplyRequest.class);
    verify(aiAgentClient).generate(replyCaptor.capture());
    assertThat(replyCaptor.getValue().recentMessages()).hasSize(20);
  }

  @Test
  void sendMessageRejectsInactiveConversationBeforeExternalCalls() {
    Conversation conversation = Conversation.create("customer-1");
    conversation.end();
    String conversationNo = conversation.getConversationNo();
    SendMessageCommand command = new SendMessageCommand();
    command.setConversationNo(conversationNo);
    command.setContent("Hello");
    when(conversationRepository.findByConversationNo(conversationNo))
        .thenReturn(Optional.of(conversation));

    assertThatThrownBy(() -> service.sendMessage(command, "key-1"))
        .isInstanceOf(ConversationNotActiveException.class);

    verify(conversationRepository, never()).saveAndFlush(any());
    verifyNoInteractions(aiAgentClient, orderQueryClient);
  }

  @Test
  void sendMessagePropagatesAgentFailureBeforeAgentReplyIsSaved() {
    Conversation conversation = Conversation.create("customer-1");
    String conversationNo = conversation.getConversationNo();
    SendMessageCommand command = new SendMessageCommand();
    command.setConversationNo(conversationNo);
    command.setContent("Hello");
    when(conversationRepository.findByConversationNo(conversationNo))
        .thenReturn(Optional.of(conversation));
    when(conversationRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    when(orderQueryClient.getRecentOrders("customer-1")).thenReturn(List.of());
    when(aiAgentClient.generate(any()))
        .thenThrow(new org.acm.cs.application.port.out.AiAgentUnavailableException("agent down"));

    assertThatThrownBy(() -> service.sendMessage(command, "key-1"))
        .isInstanceOf(org.acm.cs.application.port.out.AiAgentUnavailableException.class)
        .hasMessage("agent down");

    // customer message was flushed once before the agent call; the agent reply must never
    // be flushed. The outer transaction (real IdempotencyService) rolls both back.
    verify(conversationRepository, times(1)).saveAndFlush(any());
    verify(orderQueryClient).getRecentOrders("customer-1");
    verify(aiAgentClient, times(1)).generate(any());
  }

  @Test
  void endConversationPassesIdempotentOperationAndEnds() {
    Conversation conversation = Conversation.create("customer-1");
    String conversationNo = conversation.getConversationNo();
    when(conversationRepository.findByConversationNo(conversationNo))
        .thenReturn(Optional.of(conversation));
    when(conversationRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    @SuppressWarnings("unchecked")
    ArgumentCaptor<IdempotencyService.IdempotentOperation<Conversation>> operationCaptor =
        ArgumentCaptor.forClass(IdempotencyService.IdempotentOperation.class);

    Conversation result = service.endConversation(conversationNo, "key-1");

    verify(idempotencyService).execute(operationCaptor.capture(), any());
    IdempotencyService.IdempotentOperation<Conversation> operation = operationCaptor.getValue();
    assertThat(operation.operation()).isEqualTo(ConversationService.END_OPERATION);
    assertThat(operation.idempotencyKey()).isEqualTo("key-1");
    assertThat(operation.request()).isEqualTo(conversationNo);
    assertThat(operation.responseType()).isEqualTo(Conversation.class);

    assertThat(result.getStatus()).isEqualTo(ConversationStatus.AWAITING_FEEDBACK);
    assertThat(result.getEndedAt()).isNotNull();
    verify(conversationRepository).saveAndFlush(conversation);
  }

  @Test
  void submitFeedbackPassesIdempotentOperationAndRecordsFeedback() {
    Conversation conversation = Conversation.create("customer-1");
    conversation.end();
    String conversationNo = conversation.getConversationNo();
    SubmitFeedbackCommand command = new SubmitFeedbackCommand();
    command.setConversationNo(conversationNo);
    command.setRating(FeedbackRating.SATISFIED);
    command.setComment("Great service");
    when(conversationRepository.findByConversationNo(conversationNo))
        .thenReturn(Optional.of(conversation));
    when(conversationRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    @SuppressWarnings("unchecked")
    ArgumentCaptor<IdempotencyService.IdempotentOperation<Conversation>> operationCaptor =
        ArgumentCaptor.forClass(IdempotencyService.IdempotentOperation.class);

    Conversation result = service.submitFeedback(command, "key-1");

    verify(idempotencyService).execute(operationCaptor.capture(), any());
    IdempotencyService.IdempotentOperation<Conversation> operation = operationCaptor.getValue();
    assertThat(operation.operation()).isEqualTo(ConversationService.FEEDBACK_OPERATION);
    assertThat(operation.idempotencyKey()).isEqualTo("key-1");
    assertThat(operation.request()).isSameAs(command);
    assertThat(operation.responseType()).isEqualTo(Conversation.class);

    assertThat(result.getStatus()).isEqualTo(ConversationStatus.ENDED);
    assertThat(result.getFeedback()).isNotNull();
    assertThat(result.getFeedback().getRating()).isEqualTo(FeedbackRating.SATISFIED);
    assertThat(result.getFeedback().getComment()).isEqualTo("Great service");
    verify(conversationRepository).saveAndFlush(conversation);
  }

  @Test
  void searchUsesDefaultSortAndCustomerIdRepository() {
    SearchConversationQuery query = new SearchConversationQuery();
    query.setCustomerId("customer-1");
    query.setStatus(null);
    query.setPage(1);
    query.setSize(20);
    Page<Conversation> expected = new PageImpl<>(List.of());
    when(conversationRepository.findByCustomerId(eq("customer-1"), any())).thenReturn(expected);

    Page<Conversation> result = service.search(query);

    assertThat(result).isSameAs(expected);
    verify(conversationRepository).findByCustomerId(eq("customer-1"), pageRequestCaptor.capture());
    PageRequest request = pageRequestCaptor.getValue();
    assertThat(request.getPageNumber()).isZero();
    assertThat(request.getPageSize()).isEqualTo(20);
    assertThat(request.getSort().getOrderFor("createdAt").getDirection())
        .isEqualTo(Sort.Direction.DESC);
  }

  @Test
  void searchUsesStatusRepositoryWhenStatusProvided() {
    SearchConversationQuery query = new SearchConversationQuery();
    query.setCustomerId("customer-1");
    query.setStatus(ConversationStatus.ACTIVE);
    query.setPage(1);
    query.setSize(20);
    Page<Conversation> expected = new PageImpl<>(List.of());
    when(conversationRepository.findByCustomerIdAndStatus(
            eq("customer-1"), eq(ConversationStatus.ACTIVE), any()))
        .thenReturn(expected);

    assertThat(service.search(query)).isSameAs(expected);

    verify(conversationRepository)
        .findByCustomerIdAndStatus(
            eq("customer-1"), eq(ConversationStatus.ACTIVE), pageRequestCaptor.capture());
    PageRequest request = pageRequestCaptor.getValue();
    assertThat(request.getPageNumber()).isZero();
    assertThat(request.getPageSize()).isEqualTo(20);
    assertThat(request.getSort().getOrderFor("createdAt").getDirection())
        .isEqualTo(Sort.Direction.DESC);
  }

  @Test
  void findByConversationNoReturnsLoadedConversation() {
    Conversation conversation = Conversation.create("customer-1");
    String conversationNo = conversation.getConversationNo();
    when(conversationRepository.findByConversationNo(conversationNo))
        .thenReturn(Optional.of(conversation));

    assertThat(service.findByConversationNo(conversationNo)).isSameAs(conversation);
  }

  @Test
  void findByConversationNoThrowsWhenAbsent() {
    when(conversationRepository.findByConversationNo("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.findByConversationNo("missing"))
        .isInstanceOf(ConversationNotFoundException.class)
        .hasMessage("Conversation 'missing' not found");
  }

  @Test
  void listQuickQuestionsMapsEnabledQuickQuestionsToItems() {
    QuickQuestion qq1 = quickQuestion(1L, 1, "How to track my order?");
    QuickQuestion qq2 = quickQuestion(2L, 2, "How to return?");
    when(quickQuestionRepository.findByEnabledTrueOrderBySortOrderAsc())
        .thenReturn(List.of(qq1, qq2));

    List<QuickQuestionItem> result = service.listQuickQuestions();

    assertThat(result)
        .extracting(QuickQuestionItem::id, QuickQuestionItem::sortOrder, QuickQuestionItem::questionText)
        .containsExactly(
            tuple(1L, 1, "How to track my order?"),
            tuple(2L, 2, "How to return?"));
  }

  private static QuickQuestion quickQuestion(Long id, int sortOrder, String questionText) {
    QuickQuestion qq = new QuickQuestion();
    qq.setId(id);
    qq.setSortOrder(sortOrder);
    qq.setQuestionText(questionText);
    qq.setEnabled(true);
    return qq;
  }
}
