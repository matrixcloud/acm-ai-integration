package org.acm.ca.interfaces.http.controller;

import jakarta.validation.Valid;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.acm.ca.application.port.in.ConversationUseCase;
import org.acm.ca.application.port.in.command.SendMessageCommand;
import org.acm.ca.domain.conversation.Conversation;
import org.acm.ca.domain.shared.BusinessException;
import org.acm.ca.interfaces.http.mapper.ConversationRequestMapper;
import org.acm.ca.interfaces.http.mapper.ConversationResponseMapper;
import org.acm.ca.interfaces.http.request.CreateConversationRequest;
import org.acm.ca.interfaces.http.request.SearchConversationRequest;
import org.acm.ca.interfaces.http.request.SendMessageRequest;
import org.acm.ca.interfaces.http.request.SubmitFeedbackRequest;
import org.acm.ca.interfaces.http.response.ConversationDetailResponse;
import org.acm.ca.interfaces.http.response.ConversationSummaryResponse;
import org.acm.common.http.PageResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequestMapping("/conversations")
public class ConversationController {

  private final ConversationUseCase conversationService;
  private final ConversationRequestMapper requestMapper;
  private final ConversationResponseMapper responseMapper;
  private final Executor executor;

  public ConversationController(
      ConversationUseCase conversationService,
      ConversationRequestMapper requestMapper,
      ConversationResponseMapper responseMapper,
      @Qualifier("sseExecutor") Executor executor) {
    this.conversationService = conversationService;
    this.requestMapper = requestMapper;
    this.responseMapper = responseMapper;
    this.executor = executor;
  }

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

  /**
   * Streams the AI reply for a customer message as SSE. Request-level problems (body/header
   * validation) reject before the stream is established; once streaming, all business and
   * external-dependency failures are reported in-band via the {@code error} event.
   */
  @PostMapping(value = "/{conversationNo}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter sendMessage(
      @PathVariable String conversationNo,
      @Valid @RequestBody SendMessageRequest request,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    SseEmitter emitter = new SseEmitter(60_000L);
    SendMessageCommand command = new SendMessageCommand();
    command.setConversationNo(conversationNo);
    command.setContent(request.getContent());
    executor.execute(
        () -> {
          ConversationSseStream stream = new ConversationSseStream(emitter, responseMapper);
          try {
            conversationService.streamMessage(command, idempotencyKey, stream);
          } catch (BusinessException e) {
            stream.emitError(e.code(), e.getMessage());
          } catch (Exception e) {
            log.warn("conversation message stream failed conversationNo={}", conversationNo, e);
            emitter.completeWithError(e);
          }
        });
    return emitter;
  }

  @PostMapping("/{conversationNo}/end")
  public ResponseEntity<ConversationDetailResponse> endConversation(
      @PathVariable String conversationNo,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    Conversation conversation = conversationService.endConversation(conversationNo, idempotencyKey);
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
