package org.acm.ca.interfaces.http.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.acm.ca.application.port.in.ConversationUseCase;
import org.acm.ca.application.port.in.ConversationUseCase.QuickQuestionItem;
import org.acm.ca.interfaces.http.mapper.ConversationResponseMapper;
import org.acm.ca.interfaces.http.response.QuickQuestionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuickQuestionControllerTest {

  @Mock private ConversationUseCase conversationService;
  @Mock private ConversationResponseMapper responseMapper;

  private QuickQuestionController controller;

  @BeforeEach
  void setUp() {
    controller = new QuickQuestionController(conversationService, responseMapper);
  }

  @Test
  void listReturnsQuickQuestionResponses() {
    QuickQuestionItem item = new QuickQuestionItem(1L, 1, "我的订单到哪了？");
    QuickQuestionResponse response = new QuickQuestionResponse();
    when(conversationService.listQuickQuestions()).thenReturn(List.of(item));
    when(responseMapper.toQuickQuestionResponseList(List.of(item))).thenReturn(List.of(response));

    List<QuickQuestionResponse> result = controller.list();

    assertThat(result).containsExactly(response);
    verify(conversationService).listQuickQuestions();
  }
}
