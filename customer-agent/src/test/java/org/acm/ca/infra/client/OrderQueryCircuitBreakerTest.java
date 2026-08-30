package org.acm.ca.infra.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import java.time.Duration;
import java.util.Map;
import org.acm.ca.application.port.out.OrderQueryClient;
import org.acm.ca.application.port.out.OrderQueryUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigurationProperties;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.httpservice.CircuitBreakerAdapterDecorator;
import org.springframework.cloud.client.circuitbreaker.httpservice.CircuitBreakerRequestValueProcessor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * Exercises the circuit breaker the way Spring Cloud wires it for HTTP service groups: the group's
 * exchange adapter is decorated with {@link CircuitBreakerAdapterDecorator} around a circuit
 * breaker named after the group, configured through {@link
 * HttpServiceResilienceConfiguration#configureHttpServiceGroups}.
 */
class OrderQueryCircuitBreakerTest {

  private static final String REQUEST_URL =
      "http://order-svc/orders"
          + "?customerId=customer-001&page=1&size=20"
          + "&sortBy=createdAt&direction=DESC";

  @Test
  void opensAtFailureRateThresholdAndBlocksDownstreamCalls() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://order-svc");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    OrderQueryClient client = new OrderQueryClientImpl(client(builder, productionCircuitBreaker()));
    // 生产参数 minimumNumberOfCalls=10、failureRateThreshold=50%：5 失败 + 5 成功后打开
    for (int i = 0; i < 5; i++) {
      server.expect(once(), requestTo(REQUEST_URL)).andRespond(withServerError());
    }
    for (int i = 0; i < 5; i++) {
      server.expect(once(), requestTo(REQUEST_URL))
          .andRespond(withSuccess("{\"items\":[]}", MediaType.APPLICATION_JSON));
    }

    for (int i = 0; i < 5; i++) {
      assertThatThrownBy(() -> client.getRecentOrders("customer-001"))
          .isInstanceOf(OrderQueryUnavailableException.class);
    }
    for (int i = 0; i < 5; i++) {
      assertThat(client.getRecentOrders("customer-001")).isEmpty();
    }

    // Open 状态：立即失败，不再访问下游
    assertThatThrownBy(() -> client.getRecentOrders("customer-001"))
        .isInstanceOf(OrderQueryUnavailableException.class)
        .hasMessage("Order service circuit breaker is open");
    server.verify();
  }

  @Test
  void recoversThroughHalfOpenAfterSuccessfulProbe() throws InterruptedException {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://order-svc");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    OrderQueryClient client = new OrderQueryClientImpl(client(builder, tunedCircuitBreaker()));
    server.expect(once(), requestTo(REQUEST_URL)).andRespond(withServerError());
    server.expect(once(), requestTo(REQUEST_URL)).andRespond(withServerError());
    server.expect(once(), requestTo(REQUEST_URL))
        .andRespond(withSuccess("{\"items\":[]}", MediaType.APPLICATION_JSON));
    // Half-Open 探测与恢复后的常规调用
    server.expect(once(), requestTo(REQUEST_URL))
        .andRespond(withSuccess("{\"items\":[]}", MediaType.APPLICATION_JSON));
    server.expect(once(), requestTo(REQUEST_URL))
        .andRespond(withSuccess("{\"items\":[]}", MediaType.APPLICATION_JSON));

    // 3 次调用中 2 次失败（66% ≥ 50%）→ OPEN
    assertThatThrownBy(() -> client.getRecentOrders("customer-001"))
        .isInstanceOf(OrderQueryUnavailableException.class);
    assertThatThrownBy(() -> client.getRecentOrders("customer-001"))
        .isInstanceOf(OrderQueryUnavailableException.class);
    assertThat(client.getRecentOrders("customer-001")).isEmpty();
    assertThatThrownBy(() -> client.getRecentOrders("customer-001"))
        .isInstanceOf(OrderQueryUnavailableException.class)
        .hasMessage("Order service circuit breaker is open");

    // 等待 waitDurationInOpenState(50ms) 后放行 1 次探测调用，成功 → CLOSED
    Thread.sleep(100);
    assertThat(client.getRecentOrders("customer-001")).isEmpty();
    assertThat(client.getRecentOrders("customer-001")).isEmpty();

    server.verify();
  }

  @Test
  void clientErrorsDoNotPushFailureRate() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://order-svc");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    OrderQueryClient client = new OrderQueryClientImpl(client(builder, productionCircuitBreaker()));
    // 5 次 400（客户端/契约问题，不计入失败率）+ 5 次成功：若计入则 50% 已打开
    for (int i = 0; i < 5; i++) {
      server.expect(once(), requestTo(REQUEST_URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));
    }
    for (int i = 0; i < 6; i++) {
      server.expect(once(), requestTo(REQUEST_URL))
          .andRespond(withSuccess("{\"items\":[]}", MediaType.APPLICATION_JSON));
    }

    for (int i = 0; i < 5; i++) {
      assertThatThrownBy(() -> client.getRecentOrders("customer-001"))
          .isInstanceOf(OrderQueryUnavailableException.class);
    }
    for (int i = 0; i < 5; i++) {
      assertThat(client.getRecentOrders("customer-001")).isEmpty();
    }

    // 仍为 CLOSED：第 11 次调用直达下游
    assertThat(client.getRecentOrders("customer-001")).isEmpty();
    server.verify();
  }

  private static CircuitBreaker productionCircuitBreaker() {
    Resilience4JConfigurationProperties properties = new Resilience4JConfigurationProperties();
    properties.setDisableTimeLimiter(true);
    Resilience4JCircuitBreakerFactory factory =
        new Resilience4JCircuitBreakerFactory(
            CircuitBreakerRegistry.ofDefaults(), TimeLimiterRegistry.ofDefaults(), null, properties);
    HttpServiceResilienceConfiguration.configureHttpServiceGroups(factory);
    return factory.create(OrderServiceHttpClientConfiguration.ORDER_SERVICE_GROUP);
  }

  private static CircuitBreaker tunedCircuitBreaker() {
    Resilience4JConfigurationProperties properties = new Resilience4JConfigurationProperties();
    properties.setDisableTimeLimiter(true);
    Resilience4JCircuitBreakerFactory factory =
        new Resilience4JCircuitBreakerFactory(
            CircuitBreakerRegistry.ofDefaults(), TimeLimiterRegistry.ofDefaults(), null, properties);
    // 与生产同构的最小阈值组合：让状态迁移在毫秒级完成
    factory.configure(
        builder ->
            builder.circuitBreakerConfig(
                CircuitBreakerConfig.custom()
                    .slidingWindowSize(3)
                    .minimumNumberOfCalls(3)
                    .failureRateThreshold(50)
                    .waitDurationInOpenState(Duration.ofMillis(50))
                    .permittedNumberOfCallsInHalfOpenState(1)
                    .recordException(HttpServiceResilienceConfiguration::isDownstreamHealthFailure)
                    .build()),
        OrderServiceHttpClientConfiguration.ORDER_SERVICE_GROUP);
    return factory.create(OrderServiceHttpClientConfiguration.ORDER_SERVICE_GROUP);
  }

  private static OrderServiceHttpClient client(RestClient.Builder builder, CircuitBreaker cb) {
    RestClientAdapter adapter = RestClientAdapter.create(builder.build());
    return HttpServiceProxyFactory.builderFor(adapter)
        .httpRequestValuesProcessor(new CircuitBreakerRequestValueProcessor())
        .exchangeAdapterDecorator(
            underlying -> new CircuitBreakerAdapterDecorator(underlying, cb, Map.of()))
        .build()
        .createClient(OrderServiceHttpClient.class);
  }
}
