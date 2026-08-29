package org.acm.ca.interfaces.http.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.acm.ca.application.port.out.OrderQueryClient.OrderSummary;
import org.acm.ca.domain.quickquestion.QuickQuestion;
import org.acm.ca.domain.quickquestion.QuickQuestionRepository;
import org.acm.ca.infra.client.MockAiAgentClient;
import org.acm.ca.infra.client.MockOrderQueryClient;
import org.acm.ca.interfaces.http.exception.MockAdapterInactiveException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class MockControllerTest {

  @Mock private ObjectProvider<MockAiAgentClient> aiAgentClientProvider;
  @Mock private MockAiAgentClient aiAgentClient;
  @Mock private MockOrderQueryClient orderQueryClient;
  @Mock private QuickQuestionRepository quickQuestionRepository;

  private MockController controller;

  @BeforeEach
  void setUp() {
    controller =
        new MockController(aiAgentClientProvider, orderQueryClient, quickQuestionRepository);
    lenient().when(aiAgentClientProvider.getIfAvailable()).thenReturn(aiAgentClient);
  }

  @Test
  void setReplyRuleDelegatesToAiAgentClient() {
    controller.setReplyRule(new MockController.ReplyRuleRequest("投诉", "已记录"));

    verify(aiAgentClient).setReplyRule("投诉", "已记录");
  }

  @Test
  void agentEndpointsFailFastWhenMockAdapterIsInactive() {
    when(aiAgentClientProvider.getIfAvailable()).thenReturn(null);

    assertThatThrownBy(
            () -> controller.setReplyRule(new MockController.ReplyRuleRequest("投诉", "已记录")))
        .isInstanceOf(MockAdapterInactiveException.class);
    assertThatThrownBy(
            () -> controller.setFailure("ai-agent", new MockController.SetFailureRequest(true)))
        .isInstanceOf(MockAdapterInactiveException.class);
  }

  @Test
  void setOrdersDelegatesToOrderQueryClient() {
    List<OrderSummary> orders =
        List.of(
            new OrderSummary(
                "ORD-1", "PAID", java.math.BigDecimal.TEN, "CNY", java.time.LocalDateTime.now()));
    controller.setOrders("customer-002", new MockController.SetOrdersRequest(orders));

    verify(orderQueryClient).setOrders("customer-002", orders);
  }

  @Test
  void setFailureForAiAgent() {
    controller.setFailure("ai-agent", new MockController.SetFailureRequest(true));

    verify(aiAgentClient).setFailure(true);
  }

  @Test
  void setFailureForOrderQuery() {
    controller.setFailure("order-query", new MockController.SetFailureRequest(true));

    verify(orderQueryClient).setFailure(true);
  }

  @Test
  void setFailureRejectsUnknownCapability() {
    assertThatThrownBy(
            () -> controller.setFailure("unknown", new MockController.SetFailureRequest(true)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unknown capability 'unknown'");
  }

  @Test
  void setFailureDoesNotEnableWhenFlagIsFalse() {
    controller.setFailure("ai-agent", new MockController.SetFailureRequest(false));

    verify(aiAgentClient).setFailure(false);
  }

  @Test
  void addQuickQuestionPersistsEnabledQuickQuestion() {
    when(quickQuestionRepository.save(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    QuickQuestion result =
        controller.addQuickQuestion(
            new MockController.AddQuickQuestionRequest(10, "如何修改密码？"));

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
