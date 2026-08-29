package org.acm.ca.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.acm.ca.application.port.out.AiAgentClient;
import org.acm.ca.infra.client.MockAiAgentClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Mock adapter selection: with {@code customer.adapters.ai-agent=mock} the in-memory mock backs
 * the {@link AiAgentClient} port and the real in-process adapter is absent (design §2.4).
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "eureka.client.enabled=false",
      "spring.ai.openai.api-key=test-key",
      "customer.adapters.ai-agent=mock"
    })
class MockAdapterSelectionTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

  @Autowired private AiAgentClient aiAgentClient;
  @Autowired private org.springframework.context.ApplicationContext context;

  @Test
  void mockAdapterBacksThePortAndRealAdapterIsAbsent() {
    assertThat(aiAgentClient).isInstanceOf(MockAiAgentClient.class);
    assertThat(context.getBeansOfType(InProcessAiAgentClient.class)).isEmpty();
  }
}
