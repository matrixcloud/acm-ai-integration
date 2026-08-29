package org.acm.cs.application.port.out;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface OrderQueryClient {

  List<OrderSummary> getRecentOrders(String customerId);

  record OrderSummary(
      String orderNo,
      String status,
      BigDecimal payableTotal,
      String currency,
      LocalDateTime createdAt) {}
}
