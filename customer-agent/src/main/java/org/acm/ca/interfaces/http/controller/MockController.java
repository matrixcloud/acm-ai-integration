package org.acm.ca.interfaces.http.controller;

import org.acm.ca.domain.quickquestion.QuickQuestion;
import org.acm.ca.domain.quickquestion.QuickQuestionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mock")
public class MockController {

  private final QuickQuestionRepository quickQuestionRepository;

  public MockController(QuickQuestionRepository quickQuestionRepository) {
    this.quickQuestionRepository = quickQuestionRepository;
  }

  @PostMapping("/quick-questions")
  @ResponseStatus(HttpStatus.CREATED)
  public QuickQuestion addQuickQuestion(@RequestBody AddQuickQuestionRequest request) {
    QuickQuestion quickQuestion = new QuickQuestion();
    quickQuestion.setSortOrder(request.sortOrder());
    quickQuestion.setQuestionText(request.questionText());
    quickQuestion.setEnabled(true);
    return quickQuestionRepository.save(quickQuestion);
  }

  public record AddQuickQuestionRequest(Integer sortOrder, String questionText) {}
}
