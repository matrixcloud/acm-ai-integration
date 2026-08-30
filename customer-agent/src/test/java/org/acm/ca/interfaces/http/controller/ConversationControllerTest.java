package org.acm.ca.interfaces.http.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.Executor;
import org.acm.ca.application.port.in.ConversationUseCase;
import org.acm.ca.application.port.in.command.CreateConversationCommand;
import org.acm.ca.application.port.in.command.SendMessageCommand;
import org.acm.ca.application.port.in.command.SubmitFeedbackCommand;
import org.acm.ca.application.port.in.query.SearchConversationQuery;
import org.acm.ca.domain.conversation.Conversation;
import org.acm.ca.domain.conversation.FeedbackRating;
import org.acm.ca.interfaces.http.mapper.ConversationRequestMapper;
import org.acm.ca.interfaces.http.mapper.ConversationResponseMapper;
import org.acm.ca.interfaces.http.request.CreateConversationRequest;
import org.acm.ca.interfaces.http.request.SearchConversationRequest;
import org.acm.ca.interfaces.http.request.SendMessageRequest;
import org.acm.ca.interfaces.http.request.SubmitFeedbackRequest;
import org.acm.ca.interfaces.http.response.ConversationDetailResponse;
import org.acm.ca.interfaces.http.response.ConversationSummaryResponse;
import org.acm.common.http.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
class ConversationControllerTest {

  @Mock private ConversationUseCase conversationService;
  @Mock private ConversationRequestMapper requestMapper;
  @Mock private ConversationResponseMapper responseMapper;

  private ConversationController controller;

  @BeforeEach
  void setUp() {
    Executor directExecutor = Runnable::run;
    controller =
        new ConversationController(
            conversationService, requestMapper, responseMapper, directExecutor);
  }

  @Test
  void createMapsRequestAndReturnsCreatedResponse() {
    CreateConversationRequest request = new CreateConversationRequest();
    request.setCustomerId("customer-001");
    CreateConversationCommand command = new CreateConversationCommand();
    command.setCustomerId("customer-001");
    Conversation conversation = Conversation.create("customer-001");
    ConversationDetailResponse response = new ConversationDetailResponse();
    when(requestMapper.toCommand(request)).thenReturn(command);
    when(conversationService.create(command, "key-1")).thenReturn(conversation);
    when(responseMapper.toDetailResponse(conversation)).thenReturn(response);

    ResponseEntity<ConversationDetailResponse> result = controller.create(request, "key-1");

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(result.getBody()).isSameAs(response);
    verify(conversationService).create(command, "key-1");
  }

  @Test
  void searchMapsPageAndUsesZeroBasedResponseMetadata() {
    SearchConversationRequest request = new SearchConversationRequest();
    request.setCustomerId("customer-001");
    SearchConversationQuery query = new SearchConversationQuery();
    query.setCustomerId("customer-001");
    Conversation conversation = Conversation.create("customer-001");
    ConversationSummaryResponse response = new ConversationSummaryResponse();
    when(requestMapper.toQuery(request)).thenReturn(query);
    when(conversationService.search(query))
        .thenReturn(new PageImpl<>(List.of(conversation), PageRequest.of(1, 2), 5));
    when(responseMapper.toSummaryResponseList(List.of(conversation))).thenReturn(List.of(response));

    PageResponse<ConversationSummaryResponse> result = controller.search(request);

    assertThat(result.getItems()).containsExactly(response);
    assertThat(result.getPage().getNumber()).isEqualTo(1);
    assertThat(result.getPage().getSize()).isEqualTo(2);
    assertThat(result.getPage().getTotalElements()).isEqualTo(5);
    assertThat(result.getPage().getTotalPages()).isEqualTo(3);
  }

  @Test
  void findByConversationNoReturnsOkWithDetail() {
    Conversation conversation = Conversation.create("customer-001");
    ConversationDetailResponse response = new ConversationDetailResponse();
    when(conversationService.findByConversationNo("CON-1")).thenReturn(conversation);
    when(responseMapper.toDetailResponse(conversation)).thenReturn(response);

    ResponseEntity<ConversationDetailResponse> result = controller.findByConversationNo("CON-1");

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody()).isSameAs(response);
  }

  @Test
  void sendMessageStreamsReplyThroughConversationStream() {
    SendMessageRequest request = new SendMessageRequest();
    request.setContent("我的订单到哪了？");

    SseEmitter emitter = controller.sendMessage("CON-1", request, "key-1");

    assertThat(emitter).isNotNull();
    ArgumentCaptor<SendMessageCommand> commandCaptor =
        ArgumentCaptor.forClass(SendMessageCommand.class);
    verify(conversationService).streamMessage(commandCaptor.capture(), eq("key-1"), any());
    assertThat(commandCaptor.getValue().getConversationNo()).isEqualTo("CON-1");
    assertThat(commandCaptor.getValue().getContent()).isEqualTo("我的订单到哪了？");
  }

  @Test
  void endConversationReturnsAcceptedWithDetail() {
    Conversation conversation = Conversation.create("customer-001");
    conversation.end();
    ConversationDetailResponse response = new ConversationDetailResponse();
    when(conversationService.endConversation("CON-1", "key-1")).thenReturn(conversation);
    when(responseMapper.toDetailResponse(conversation)).thenReturn(response);

    ResponseEntity<ConversationDetailResponse> result =
        controller.endConversation("CON-1", "key-1");

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(result.getBody()).isSameAs(response);
  }

  @Test
  void submitFeedbackReturnsOkWithDetail() {
    SubmitFeedbackRequest request = new SubmitFeedbackRequest();
    request.setRating(FeedbackRating.SATISFIED);
    request.setComment("回复很快");
    SubmitFeedbackCommand command = new SubmitFeedbackCommand();
    command.setConversationNo("CON-1");
    command.setRating(FeedbackRating.SATISFIED);
    command.setComment("回复很快");
    Conversation conversation = Conversation.create("customer-001");
    conversation.end();
    conversation.submitFeedback(FeedbackRating.SATISFIED, "回复很快");
    ConversationDetailResponse response = new ConversationDetailResponse();
    when(requestMapper.toCommand("CON-1", request)).thenReturn(command);
    when(conversationService.submitFeedback(command, "key-1")).thenReturn(conversation);
    when(responseMapper.toDetailResponse(conversation)).thenReturn(response);

    ResponseEntity<ConversationDetailResponse> result =
        controller.submitFeedback("CON-1", request, "key-1");

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody()).isSameAs(response);
  }
}
