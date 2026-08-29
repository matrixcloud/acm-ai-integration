package org.acm.ca.interfaces.http.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.acm.ca.application.port.in.ConversationUseCase;
import org.acm.ca.interfaces.http.mapper.ConversationResponseMapper;
import org.acm.ca.interfaces.http.response.QuickQuestionResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/quick-questions")
@RequiredArgsConstructor
public class QuickQuestionController {

  private final ConversationUseCase conversationService;
  private final ConversationResponseMapper responseMapper;

  @GetMapping
  public List<QuickQuestionResponse> list() {
    return responseMapper.toQuickQuestionResponseList(conversationService.listQuickQuestions());
  }
}
