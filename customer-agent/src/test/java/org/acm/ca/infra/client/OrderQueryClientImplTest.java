package org.acm.ca.infra.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.LocalDateTime;
import java.util.List;
import org.acm.ca.application.port.out.OrderQueryClient.OrderSummary;
import org.acm.ca.application.port.out.OrderQueryUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

class OrderQueryClientImplTest {

  @Test
  void getRecentOrdersCallsOrderServiceAndMapsResponse() {
    Fixture fixture = fixture();
    fixture.server()
        .expect(
            requestTo(
                "http://order-svc/orders"
                    + "?customerId=customer-001&page=1&size=20"
                    + "&sortBy=createdAt&direction=DESC"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("API-Version", "1"))
        .andExpect(queryParam("customerId", "customer-001"))
        .andExpect(queryParam("page", "1"))
        .andExpect(queryParam("size", "20"))
        .andExpect(queryParam("sortBy", "createdAt"))
        .andExpect(queryParam("direction", "DESC"))
        .andRespond(
            withSuccess(
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
                """,
                MediaType.APPLICATION_JSON));

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
    fixture.server()
        .expect(
            requestTo(
                "http://order-svc/orders"
                    + "?customerId=customer-404&page=1&size=20"
                    + "&sortBy=createdAt&direction=DESC"))
        .andRespond(withSuccess("{\"items\":[]}", MediaType.APPLICATION_JSON));

    assertThat(fixture.client().getRecentOrders("customer-404")).isEmpty();
    fixture.server().verify();
  }

  @Test
  void getRecentOrdersRejectsMissingItems() {
    Fixture fixture = fixture();
    fixture.server()
        .expect(
            requestTo(
                "http://order-svc/orders"
                    + "?customerId=customer-001&page=1&size=20"
                    + "&sortBy=createdAt&direction=DESC"))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> fixture.client().getRecentOrders("customer-001"))
        .isInstanceOf(OrderQueryUnavailableException.class)
        .hasMessage("Order query response items must not be null");
    fixture.server().verify();
  }

  @Test
  void getRecentOrdersRejectsMissingResponseBody() {
    Fixture fixture = fixture();
    fixture.server()
        .expect(
            requestTo(
                "http://order-svc/orders"
                    + "?customerId=customer-001&page=1&size=20"
                    + "&sortBy=createdAt&direction=DESC"))
        .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> fixture.client().getRecentOrders("customer-001"))
        .isInstanceOf(OrderQueryUnavailableException.class)
        .hasMessage("Order query response body must not be null");
    fixture.server().verify();
  }

  @Test
  void getRecentOrdersConvertsHttpFailure() {
    Fixture fixture = fixture();
    fixture.server()
        .expect(
            requestTo(
                "http://order-svc/orders"
                    + "?customerId=customer-001&page=1&size=20"
                    + "&sortBy=createdAt&direction=DESC"))
        .andRespond(withServerError());

    assertThatThrownBy(() -> fixture.client().getRecentOrders("customer-001"))
        .isInstanceOf(OrderQueryUnavailableException.class)
        .hasMessage("Order query failed")
        .hasCauseInstanceOf(Exception.class);
    fixture.server().verify();
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

  private record Fixture(OrderQueryClientImpl client, MockRestServiceServer server) {}
}
