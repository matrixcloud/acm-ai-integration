package org.acm.cs.interfaces.http.controller;

import org.acm.cs.domain.quickquestion.QuickQuestion;
import org.acm.cs.domain.quickquestion.QuickQuestionRepository;
import org.acm.cs.infra.client.AiAgentClientImpl;
import org.acm.cs.infra.client.OrderQueryClientImpl;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mock")
public class MockController {

  private final AiAgentClientImpl aiAgentClient;
  private final OrderQueryClientImpl orderQueryClient;
  private final QuickQuestionRepository quickQuestionRepository;

  public MockController(
      AiAgentClientImpl aiAgentClient,
      OrderQueryClientImpl orderQueryClient,
      QuickQuestionRepository quickQuestionRepository) {
    this.aiAgentClient = aiAgentClient;
    this.orderQueryClient = orderQueryClient;
    this.quickQuestionRepository = quickQuestionRepository;
  }

  @PutMapping("/agent/reply-rule")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void setReplyRule(@RequestBody ReplyRuleRequest request) {
    aiAgentClient.setReplyRule(request.keyword(), request.reply());
  }

  @PutMapping("/orders/{customerId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void setOrders(@PathVariable String customerId, @RequestBody SetOrdersRequest request) {
    orderQueryClient.setOrders(customerId, request.toSummaries());
  }

  @PutMapping("/failures/{capability}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void setFailure(@PathVariable String capability, @RequestBody SetFailureRequest request) {
    boolean shouldFail = request.enabled() != null && request.enabled();
    switch (capability) {
      case "ai-agent" -> aiAgentClient.setFailure(shouldFail);
      case "order-query" -> orderQueryClient.setFailure(shouldFail);
      default -> throw new IllegalArgumentException("Unknown capability '%s'".formatted(capability));
    }
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

  public record ReplyRuleRequest(String keyword, String reply) {}

  public record SetOrdersRequest(
      java.util.List<org.acm.cs.application.port.out.OrderQueryClient.OrderSummary> orders) {
    public java.util.List<org.acm.cs.application.port.out.OrderQueryClient.OrderSummary> toSummaries() {
      return orders == null ? java.util.List.of() : orders;
    }
  }

  public record SetFailureRequest(Boolean enabled) {}

  public record AddQuickQuestionRequest(Integer sortOrder, String questionText) {}
}
