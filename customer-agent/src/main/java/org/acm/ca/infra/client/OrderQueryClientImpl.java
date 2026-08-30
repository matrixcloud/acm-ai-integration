package org.acm.ca.infra.client;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.acm.ca.application.port.out.OrderQueryClient;
import org.acm.ca.application.port.out.OrderQueryUnavailableException;
import org.springframework.stereotype.Component;

@Component
public class OrderQueryClientImpl implements OrderQueryClient {

  static final int RECENT_ORDER_PAGE = 1;
  static final int RECENT_ORDER_SIZE = 20;
  static final String SORT_FIELD_CREATED_AT = "createdAt";
  static final String SORT_DIRECTION_DESC = "DESC";
  static final String API_VERSION = "1";

  private final OrderServiceHttpClient orderServiceHttpClient;

  public OrderQueryClientImpl(OrderServiceHttpClient orderServiceHttpClient) {
    this.orderServiceHttpClient = orderServiceHttpClient;
  }

  @Override
  public List<OrderSummary> getRecentOrders(String customerId) {
    try {
      OrderPageResponse response =
          orderServiceHttpClient.search(
              API_VERSION,
              customerId,
              RECENT_ORDER_PAGE,
              RECENT_ORDER_SIZE,
              SORT_FIELD_CREATED_AT,
              SORT_DIRECTION_DESC);
      if (response == null) {
        throw new OrderQueryUnavailableException("Order query response body must not be null");
      }
      if (response.items() == null) {
        throw new OrderQueryUnavailableException("Order query response items must not be null");
      }
      return response.items().stream()
          .map(
              item ->
                  new OrderSummary(
                      item.orderNo(),
                      item.status(),
                      item.payableTotal(),
                      item.currency(),
                      item.createdAt()))
          .toList();
    } catch (OrderQueryUnavailableException e) {
      throw e;
    } catch (Exception e) {
      throw new OrderQueryUnavailableException("Order query failed", e);
    }
  }

  public record OrderPageResponse(List<OrderItem> items) {}

  public record OrderItem(
      String orderNo,
      String status,
      BigDecimal payableTotal,
      String currency,
      LocalDateTime createdAt) {}
}
