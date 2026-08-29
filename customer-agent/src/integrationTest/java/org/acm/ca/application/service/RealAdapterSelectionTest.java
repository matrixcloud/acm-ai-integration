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

/** Default adapter selection: the real in-process agent adapter is the {@link AiAgentClient}. */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {"eureka.client.enabled=false", "spring.ai.openai.api-key=test-key"})
class RealAdapterSelectionTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

  @Autowired private AiAgentClient aiAgentClient;
  @Autowired private org.springframework.context.ApplicationContext context;

  @Test
  void inProcessAdapterIsTheDefaultAiAgentClient() {
    assertThat(aiAgentClient).isInstanceOf(InProcessAiAgentClient.class);
    assertThat(context.getBeansOfType(MockAiAgentClient.class)).isEmpty();
  }
}
