package org.acm.cs;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Design §11.2 / §17: Mock adapters are registered only under the {@code demo} profile; with the
 * profile closed and no real adapter present, the application must fail to start instead of
 * silently falling back to Mock.
 *
 * <p>{@code --spring.profiles.active} is passed as a command-line argument (highest precedence)
 * so that {@code application.yml}'s default {@code demo} profile is overridden, not merged in.
 */
@Testcontainers
class NonDemoProfileStartupTest {

  @Container
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

  @Test
  void startupFailsWithoutRealAdaptersOnNonDemoProfile() {
    assertThatThrownBy(
            () ->
                new SpringApplicationBuilder(CustomerSvcApplication.class)
                    .web(WebApplicationType.NONE)
                    .run(
                        "--spring.profiles.active=prod",
                        "--spring.datasource.url=" + postgres.getJdbcUrl(),
                        "--spring.datasource.username=" + postgres.getUsername(),
                        "--spring.datasource.password=" + postgres.getPassword()))
        .isInstanceOf(UnsatisfiedDependencyException.class);
  }
}