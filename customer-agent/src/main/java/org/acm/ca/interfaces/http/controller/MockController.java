package org.acm.ca.interfaces.http.controller;

import org.acm.ca.application.port.out.OrderQueryClient.OrderSummary;
import org.acm.ca.domain.quickquestion.QuickQuestion;
import org.acm.ca.domain.quickquestion.QuickQuestionRepository;
import org.acm.ca.infra.client.MockAiAgentClient;
import org.acm.ca.infra.client.MockOrderQueryClient;
import org.acm.ca.interfaces.http.exception.MockAdapterInactiveException;
import org.springframework.beans.factory.ObjectProvider;
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

  private final ObjectProvider<MockAiAgentClient> aiAgentClientProvider;
  private final MockOrderQueryClient orderQueryClient;
  private final QuickQuestionRepository quickQuestionRepository;

  public MockController(
      ObjectProvider<MockAiAgentClient> aiAgentClientProvider,
      MockOrderQueryClient orderQueryClient,
      QuickQuestionRepository quickQuestionRepository) {
    this.aiAgentClientProvider = aiAgentClientProvider;
    this.orderQueryClient = orderQueryClient;
    this.quickQuestionRepository = quickQuestionRepository;
  }

  @PutMapping("/agent/reply-rule")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void setReplyRule(@RequestBody ReplyRuleRequest request) {
    aiAgentClient().setReplyRule(request.keyword(), request.reply());
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
      case "ai-agent" -> aiAgentClient().setFailure(shouldFail);
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

  private MockAiAgentClient aiAgentClient() {
    MockAiAgentClient client = aiAgentClientProvider.getIfAvailable();
    if (client == null) {
      throw new MockAdapterInactiveException(
          "Mock AI agent adapter is inactive; set customer.adapters.ai-agent=mock to enable it");
    }
    return client;
  }

  public record ReplyRuleRequest(String keyword, String reply) {}

  public record SetOrdersRequest(java.util.List<OrderSummary> orders) {
    public java.util.List<OrderSummary> toSummaries() {
      return orders == null ? java.util.List.of() : orders;
    }
  }

  public record SetFailureRequest(Boolean enabled) {}

  public record AddQuickQuestionRequest(Integer sortOrder, String questionText) {}
}
