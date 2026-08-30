package org.acm.ca.infra.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.acm.ca.application.port.out.OrderQueryClient;
import org.acm.ca.application.port.out.OrderQueryClient.OrderSummary;
import org.acm.ca.application.port.out.OrderQueryContractException;
import org.acm.ca.application.port.out.OrderQueryUnavailableException;
import org.acm.ca.application.port.out.TransientOrderQueryException;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

class OrderQueryClientImplTest {

  private static final String REQUEST_URL =
      "http://order-svc/orders"
          + "?customerId=customer-001&page=1&size=20"
          + "&sortBy=createdAt&direction=DESC";
  private static final String PAGE_JSON =
      """
      {
        "items": [
          {
            "orderNo": "ORD-1",
            "status": "PAID",
            "payableTotal": 498.00,
            "currency": "CNY",
            "createdAt": "2026-08-28T10:30:00"
          }
        ],
        "page": {
          "number": 0,
          "size": 20,
          "totalElements": 1,
          "totalPages": 1
        }
      }
      """;

  @Test
  void getRecentOrdersCallsOrderServiceAndMapsResponse() {
    Fixture fixture = fixture();
    fixture.server()
        .expect(requestTo(REQUEST_URL))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("API-Version", "1"))
        .andExpect(queryParam("customerId", "customer-001"))
        .andExpect(queryParam("page", "1"))
        .andExpect(queryParam("size", "20"))
        .andExpect(queryParam("sortBy", "createdAt"))
        .andExpect(queryParam("direction", "DESC"))
        .andRespond(withSuccess(PAGE_JSON, MediaType.APPLICATION_JSON));

    List<OrderSummary> result = fixture.client().getRecentOrders("customer-001");

    assertThat(result).hasSize(1);
    OrderSummary order = result.get(0);
    assertThat(order.orderNo()).isEqualTo("ORD-1");
    assertThat(order.status()).isEqualTo("PAID");
    assertThat(order.payableTotal()).isEqualByComparingTo("498.00");
    assertThat(order.currency()).isEqualTo("CNY");
    assertThat(order.createdAt()).isEqualTo(LocalDateTime.of(2026, 8, 28, 10, 30));
    fixture.server().verify();
  }

  @Test
  void getRecentOrdersReturnsEmptyWhenItemsIsEmpty() {
    Fixture fixture = fixture();
    fixture.server().expect(requestTo(REQUEST_URL))
        .andRespond(withSuccess("{\"items\":[]}", MediaType.APPLICATION_JSON));

    assertThat(fixture.client().getRecentOrders("customer-001")).isEmpty();
    fixture.server().verify();
  }

  @Test
  void getRecentOrdersRejectsMissingItems() {
    Fixture fixture = fixture();
    fixture.server().expect(requestTo(REQUEST_URL))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> fixture.client().getRecentOrders("customer-001"))
        .isInstanceOfSatisfying(
            OrderQueryContractException.class,
            e -> assertThat(e.code()).isEqualTo("EXTERNAL_DEPENDENCY_FAILED"))
        .hasMessage("Order query response items must not be null");
    fixture.server().verify();
  }

  @Test
  void getRecentOrdersRejectsMissingResponseBody() {
    Fixture fixture = fixture();
    fixture.server().expect(requestTo(REQUEST_URL))
        .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> fixture.client().getRecentOrders("customer-001"))
        .isInstanceOf(OrderQueryContractException.class)
        .hasMessage("Order query response body must not be null");
    fixture.server().verify();
  }

  @Test
  void getRecentOrdersFailsFastOnUnexpectedStatus() {
    Fixture fixture = fixture();
    fixture.server().expect(requestTo(REQUEST_URL)).andRespond(withServerError());

    assertThatThrownBy(() -> fixture.client().getRecentOrders("customer-001"))
        .isInstanceOf(OrderQueryContractException.class)
        .hasMessage("Unexpected order-svc response status: HTTP 500 INTERNAL_SERVER_ERROR");
    fixture.server().verify();
  }

  @Test
  void retriesOnceOnServiceUnavailableThenSucceeds() {
    try (RetryFixture fixture = retryFixture()) {
      fixture.server()
          .expect(once(), requestTo(REQUEST_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
      fixture.server()
          .expect(once(), requestTo(REQUEST_URL)).andRespond(withSuccess(PAGE_JSON, MediaType.APPLICATION_JSON));

      List<OrderSummary> result = fixture.client().getRecentOrders("customer-001");

      assertThat(result).hasSize(1);
      assertThat(result.get(0).orderNo()).isEqualTo("ORD-1");
      fixture.server().verify();
    }
  }

  @Test
  void doesNotRetryOnBadRequest() {
    try (RetryFixture fixture = retryFixture()) {
      fixture.server()
          .expect(once(), requestTo(REQUEST_URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

      assertThatThrownBy(() -> fixture.client().getRecentOrders("customer-001"))
          .isInstanceOfSatisfying(
              OrderQueryContractException.class,
              e -> assertThat(e.code()).isEqualTo("EXTERNAL_DEPENDENCY_FAILED"))
          .hasMessage("Unexpected order-svc response status: HTTP 400 BAD_REQUEST");
      fixture.server().verify();
    }
  }

  @Test
  void throwsStableExceptionAfterRetriesExhaustedWithinBudget() {
    try (RetryFixture fixture = retryFixture()) {
      fixture.server()
          .expect(once(), requestTo(REQUEST_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
      fixture.server()
          .expect(once(), requestTo(REQUEST_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

      long start = System.nanoTime();
      assertThatThrownBy(() -> fixture.client().getRecentOrders("customer-001"))
          .isInstanceOfSatisfying(
              TransientOrderQueryException.class,
              e -> assertThat(e.code()).isEqualTo("EXTERNAL_DEPENDENCY_FAILED"))
          .hasMessage("Order service transient failure: HTTP 503 SERVICE_UNAVAILABLE");
      Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

      assertThat(elapsed).isLessThan(Duration.ofSeconds(8));
      assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(100));
      fixture.server().verify();
    }
  }

  private Fixture fixture() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://order-svc");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    RestClientAdapter adapter = RestClientAdapter.create(builder.build());
    OrderServiceHttpClient httpClient =
        HttpServiceProxyFactory.builderFor(adapter)
            .build()
            .createClient(OrderServiceHttpClient.class);
    return new Fixture(new OrderQueryClientImpl(httpClient), server);
  }

  /**
   * Boots the {@code @Retryable} proxy through {@code @EnableResilientMethods} so retry behavior
   * is exercised exactly as in production, while the HTTP layer stays on the mock server.
   */
  private RetryFixture retryFixture() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://order-svc");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    RestClientAdapter adapter = RestClientAdapter.create(builder.build());
    OrderServiceHttpClient httpClient =
        HttpServiceProxyFactory.builderFor(adapter)
            .build()
            .createClient(OrderServiceHttpClient.class);
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    context.register(HttpServiceResilienceConfiguration.class);
    context.registerBean(OrderQueryClientImpl.class, () -> new OrderQueryClientImpl(httpClient));
    context.refresh();
    return new RetryFixture(context.getBean(OrderQueryClient.class), server, context);
  }

  private record Fixture(OrderQueryClientImpl client, MockRestServiceServer server) {}

  private record RetryFixture(
      OrderQueryClient client, MockRestServiceServer server, AnnotationConfigApplicationContext context)
      implements AutoCloseable {

    @Override
    public void close() {
      context.close();
    }
  }
}
