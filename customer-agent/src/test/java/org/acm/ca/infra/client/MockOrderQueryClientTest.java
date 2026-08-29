package org.acm.ca.infra.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.acm.ca.application.port.out.OrderQueryClient.OrderSummary;
import org.acm.ca.application.port.out.OrderQueryUnavailableException;
import org.junit.jupiter.api.Test;

class MockOrderQueryClientTest {

  private final MockOrderQueryClient client = new MockOrderQueryClient();

  @Test
  void getRecentOrdersReturnsSeededOrdersForCustomer001() {
    List<OrderSummary> orders = client.getRecentOrders("customer-001");

    assertThat(orders).hasSize(2);
    assertThat(orders).extracting(OrderSummary::orderNo)
        .containsExactly("ORD2608280001", "ORD2608270005");
  }

  @Test
  void getRecentOrdersReturnsEmptyForUnknownCustomer() {
    List<OrderSummary> orders = client.getRecentOrders("customer-999");

    assertThat(orders).isEmpty();
  }

  @Test
  void setOrdersReplacesOrdersForCustomer() {
    List<OrderSummary> orders =
        List.of(
            new OrderSummary("ORD-CUSTOM-1", "SHIPPED", new BigDecimal("199.00"), "CNY",
                LocalDateTime.of(2026, 8, 29, 9, 0, 0)));

    client.setOrders("customer-002", orders);

    List<OrderSummary> result = client.getRecentOrders("customer-002");
    assertThat(result).hasSize(1);
    assertThat(result.get(0).orderNo()).isEqualTo("ORD-CUSTOM-1");
    assertThat(result.get(0).status()).isEqualTo("SHIPPED");
  }

  @Test
  void setFailureMakesNextCallThrowAndThenResets() {
    client.setFailure(true);

    assertThatThrownBy(() -> client.getRecentOrders("customer-001"))
        .isInstanceOf(OrderQueryUnavailableException.class)
        .hasMessageContaining("Mock OrderQuery configured to fail");

    List<OrderSummary> orders = client.getRecentOrders("customer-001");
    assertThat(orders).hasSize(2);
  }
}
