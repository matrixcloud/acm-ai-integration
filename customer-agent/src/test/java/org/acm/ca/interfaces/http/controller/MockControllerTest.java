package org.acm.ca.interfaces.http.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.acm.ca.domain.quickquestion.QuickQuestion;
import org.acm.ca.domain.quickquestion.QuickQuestionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MockControllerTest {

  @Mock private QuickQuestionRepository quickQuestionRepository;

  @Test
  void addQuickQuestionPersistsEnabledQuickQuestion() {
    when(quickQuestionRepository.save(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    MockController controller = new MockController(quickQuestionRepository);

    QuickQuestion result =
        controller.addQuickQuestion(new MockController.AddQuickQuestionRequest(10, "如何修改密码？"));

    ArgumentCaptor<QuickQuestion> captor = ArgumentCaptor.forClass(QuickQuestion.class);
    verify(quickQuestionRepository).save(captor.capture());
    QuickQuestion saved = captor.getValue();
    assertThat(saved.getSortOrder()).isEqualTo(10);
    assertThat(saved.getQuestionText()).isEqualTo("如何修改密码？");
    assertThat(saved.getEnabled()).isTrue();
    assertThat(result.getSortOrder()).isEqualTo(10);
    assertThat(result.getQuestionText()).isEqualTo("如何修改密码？");
  }
}
