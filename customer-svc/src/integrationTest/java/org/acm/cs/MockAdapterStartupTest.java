package org.acm.cs;

import static org.assertj.core.api.Assertions.assertThat;

import org.acm.cs.application.port.out.AiAgentClient;
import org.acm.cs.application.port.out.OrderQueryClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Mock adapters are registered unconditionally: on a non-demo profile the application must
 * still start, with mock adapters backing the outbound ports until real HTTP adapters land.
 */
@Testcontainers
class MockAdapterStartupTest {

  @Container
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

  @Test
  void mockAdaptersRegisteredOnNonDemoProfile() {
    try (ConfigurableApplicationContext context =
        new SpringApplicationBuilder(CustomerSvcApplication.class)
            .web(WebApplicationType.NONE)
            .run(
                "--spring.profiles.active=prod",
                "--spring.datasource.url=" + postgres.getJdbcUrl(),
                "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword())) {
      assertThat(context.getBean(OrderQueryClient.class)).isNotNull();
      assertThat(context.getBean(AiAgentClient.class)).isNotNull();
    }
  }
}
