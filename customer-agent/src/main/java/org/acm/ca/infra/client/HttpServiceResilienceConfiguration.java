package org.acm.ca.infra.client;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Explicit circuit breaker configuration for the HTTP service groups. With {@code
 * spring-cloud-starter-circuitbreaker-resilience4j} on the classpath, Spring Cloud creates a
 * circuit breaker per group named after the group; this customizer tunes only the two downstream
 * groups instead of relying on global defaults.
 */
@Configuration(proxyBeanMethods = false)
@EnableResilientMethods(proxyTargetClass = true)
class HttpServiceResilienceConfiguration {

  @Bean
  Customizer<Resilience4JCircuitBreakerFactory> httpServiceGroupCircuitBreakerCustomizer() {
    return HttpServiceResilienceConfiguration::configureHttpServiceGroups;
  }

  static void configureHttpServiceGroups(Resilience4JCircuitBreakerFactory factory) {
    factory.configure(
        builder -> builder.circuitBreakerConfig(downstreamCircuitBreakerConfig()),
        OrderServiceHttpClientConfiguration.ORDER_SERVICE_GROUP,
        KbServiceHttpClientConfiguration.KB_SERVICE_GROUP);
  }

  static CircuitBreakerConfig downstreamCircuitBreakerConfig() {
    return CircuitBreakerConfig.custom()
        .slidingWindowSize(20)
        .minimumNumberOfCalls(10)
        .failureRateThreshold(50)
        .slowCallDurationThreshold(Duration.ofSeconds(4))
        .slowCallRateThreshold(50)
        .waitDurationInOpenState(Duration.ofSeconds(15))
        .permittedNumberOfCallsInHalfOpenState(3)
        .recordException(HttpServiceResilienceConfiguration::isDownstreamHealthFailure)
        .build();
  }

  /**
   * Only downstream health problems push the failure rate: connection/IO failures, 5xx and 429.
   * Client-side 4xx and response contract violations are bugs or request problems — retrying and
   * tripping the circuit cannot fix them, so they must not open the circuit.
   */
  static boolean isDownstreamHealthFailure(Throwable e) {
    return e instanceof ResourceAccessException
        || e instanceof HttpStatusCodeException http
            && (http.getStatusCode().is5xxServerError() || http.getStatusCode().value() == 429);
  }
}
