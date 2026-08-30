package org.acm.gw;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;
import java.util.List;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Explicit circuit breaker parameters for the gateway's downstream service routes. Without this,
 * resilience4j's defaults (minimum 100 calls before evaluation) would make the route-level
 * protection inert at gateway traffic levels; the parameters mirror the service-client side so both
 * layers share one protection contract.
 */
@Configuration(proxyBeanMethods = false)
class GatewayCircuitBreakerConfiguration {

  static final List<String> DOWNSTREAM_SERVICES = List.of("customer-agent", "order-svc", "kb-svc");

  @Bean
  Customizer<ReactiveResilience4JCircuitBreakerFactory> gatewayCircuitBreakerCustomizer() {
    return factory ->
        factory.configure(
            builder ->
                builder.circuitBreakerConfig(
                    CircuitBreakerConfig.custom()
                        .slidingWindowSize(20)
                        .minimumNumberOfCalls(10)
                        .failureRateThreshold(50)
                        .slowCallDurationThreshold(Duration.ofSeconds(4))
                        .slowCallRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofSeconds(15))
                        .permittedNumberOfCallsInHalfOpenState(3)
                        .build()),
            DOWNSTREAM_SERVICES.toArray(String[]::new));
  }
}
