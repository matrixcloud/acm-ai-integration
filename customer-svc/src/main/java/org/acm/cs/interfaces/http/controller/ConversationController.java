package org.acm.cs.interfaces.http.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.acm.common.http.PageResponse;
import org.acm.cs.application.port.in.ConversationUseCase;
import org.acm.cs.domain.conversation.Conversation;
import org.acm.cs.interfaces.http.mapper.ConversationRequestMapper;
import org.acm.cs.interfaces.http.mapper.ConversationResponseMapper;
import org.acm.cs.interfaces.http.request.CreateConversationRequest;
import org.acm.cs.interfaces.http.request.SearchConversationRequest;
import org.acm.cs.interfaces.http.request.SendMessageRequest;
import org.acm.cs.interfaces.http.request.SubmitFeedbackRequest;
import org.acm.cs.interfaces.http.response.ConversationDetailResponse;
import org.acm.cs.interfaces.http.response.ConversationSummaryResponse;
import org.acm.cs.interfaces.http.response.MessageThreadResponse;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/conversations")
@RequiredArgsConstructor
public class ConversationController {

  private final ConversationUseCase conversationService;
  private final ConversationRequestMapper requestMapper;
  private final ConversationResponseMapper responseMapper;

  @PostMapping
  public ResponseEntity<ConversationDetailResponse> create(
      @Valid @RequestBody CreateConversationRequest request,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    Conversation conversation =
        conversationService.create(requestMapper.toCommand(request), idempotencyKey);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(responseMapper.toDetailResponse(conversation));
  }

  @GetMapping
  public PageResponse<ConversationSummaryResponse> search(
      @Valid SearchConversationRequest request) {
    Page<Conversation> result = conversationService.search(requestMapper.toQuery(request));
    return new PageResponse<>(
        responseMapper.toSummaryResponseList(result.getContent()),
        new PageResponse.Page(
            result.getNumber(),
            result.getSize(),
            result.getTotalElements(),
            result.getTotalPages()));
  }

  @GetMapping("/{conversationNo}")
  public ResponseEntity<ConversationDetailResponse> findByConversationNo(
      @PathVariable String conversationNo) {
    Conversation conversation = conversationService.findByConversationNo(conversationNo);
    return ResponseEntity.ok(responseMapper.toDetailResponse(conversation));
  }

  @PostMapping("/{conversationNo}/messages")
  public ResponseEntity<MessageThreadResponse> sendMessage(
      @PathVariable String conversationNo,
      @Valid @RequestBody SendMessageRequest request,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    org.acm.cs.application.port.in.command.SendMessageCommand command =
        new org.acm.cs.application.port.in.command.SendMessageCommand();
    command.setConversationNo(conversationNo);
    command.setContent(request.getContent());
    ConversationUseCase.MessageThread thread =
        conversationService.sendMessage(command, idempotencyKey);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(responseMapper.toThreadResponse(thread));
  }

  @PostMapping("/{conversationNo}/end")
  public ResponseEntity<ConversationDetailResponse> endConversation(
      @PathVariable String conversationNo,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    Conversation conversation =
        conversationService.endConversation(conversationNo, idempotencyKey);
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(responseMapper.toDetailResponse(conversation));
  }

  @PostMapping("/{conversationNo}/feedback")
  public ResponseEntity<ConversationDetailResponse> submitFeedback(
      @PathVariable String conversationNo,
      @Valid @RequestBody SubmitFeedbackRequest request,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    Conversation conversation =
        conversationService.submitFeedback(
            requestMapper.toCommand(conversationNo, request), idempotencyKey);
    return ResponseEntity.ok(responseMapper.toDetailResponse(conversation));
  }

}
