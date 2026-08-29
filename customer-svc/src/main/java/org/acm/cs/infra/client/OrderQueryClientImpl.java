package org.acm.cs.infra.client;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.acm.cs.application.port.out.OrderQueryClient;
import org.acm.cs.application.port.out.OrderQueryUnavailableException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * In-memory Mock implementation of {@link OrderQueryClient} (design §11.1).
 *
 * <p>Registered only under the {@code demo} profile: outside demo, a real adapter must be
 * provided or the application fails to start (design §11.2, §17).
 */
@Component
public class OrderQueryClientImpl implements OrderQueryClient {

  private final ConcurrentHashMap<String, List<OrderSummary>> ordersByCustomer = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Boolean> failureFlags = new ConcurrentHashMap<>();

  public OrderQueryClientImpl() {
    ordersByCustomer.put(
        "customer-001",
        List.of(
            new OrderSummary(
                "ORD2608280001",
                "PAID",
                new BigDecimal("498.00"),
                "CNY",
                LocalDateTime.of(2026, 8, 28, 10, 30, 0)),
            new OrderSummary(
                "ORD2608270005",
                "COMPLETED",
                new BigDecimal("99.00"),
                "CNY",
                LocalDateTime.of(2026, 8, 27, 14, 15, 0))));
  }

  @Override
  public List<OrderSummary> getRecentOrders(String customerId) {
    if (failureFlags.getOrDefault("order-query", false)) {
      failureFlags.put("order-query", false);
      throw new OrderQueryUnavailableException(
          "Mock OrderQuery configured to fail for this call");
    }
    return new ArrayList<>(ordersByCustomer.getOrDefault(customerId, List.of()));
  }

  public void setOrders(String customerId, List<OrderSummary> orders) {
    ordersByCustomer.put(customerId, List.copyOf(orders));
  }

  public void setFailure(boolean shouldFail) {
    failureFlags.put("order-query", shouldFail);
  }
}