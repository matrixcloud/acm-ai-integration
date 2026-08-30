package org.acm.ca.infra.client;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.acm.ca.application.port.out.OrderQueryClient;
import org.acm.ca.application.port.out.OrderQueryContractException;
import org.acm.ca.application.port.out.OrderQueryUnavailableException;
import org.acm.ca.application.port.out.TransientOrderQueryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

@Component
public class OrderQueryClientImpl implements OrderQueryClient {

  private static final Logger log = LoggerFactory.getLogger(OrderQueryClientImpl.class);

  static final int RECENT_ORDER_PAGE = 1;
  static final int RECENT_ORDER_SIZE = 20;
  static final String SORT_FIELD_CREATED_AT = "createdAt";
  static final String SORT_DIRECTION_DESC = "DESC";
  static final String API_VERSION = "1";

  private final OrderServiceHttpClient orderServiceHttpClient;

  public OrderQueryClientImpl(OrderServiceHttpClient orderServiceHttpClient) {
    this.orderServiceHttpClient = orderServiceHttpClient;
  }

  @Retryable(
      includes = TransientOrderQueryException.class,
      maxRetries = 1,
      delay = 100,
      multiplier = 2,
      maxDelay = 500,
      jitter = 50,
      timeout = 8000)
  @Override
  public List<OrderSummary> getRecentOrders(String customerId) {
    long start = System.nanoTime();
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
        throw new OrderQueryContractException("Order query response body must not be null");
      }
      if (response.items() == null) {
        throw new OrderQueryContractException("Order query response items must not be null");
      }
      List<OrderSummary> orders =
          response.items().stream()
              .map(
                  item ->
                      new OrderSummary(
                          item.orderNo(),
                          item.status(),
                          item.payableTotal(),
                          item.currency(),
                          item.createdAt()))
              .toList();
      log.info(
          "http.out service=order-svc op=recent-orders customerId={} orders={} durationMs={}",
          customerId,
          orders.size(),
          (System.nanoTime() - start) / 1_000_000);
      return orders;
    } catch (OrderQueryUnavailableException e) {
      throw e;
    } catch (NoFallbackAvailableException e) {
      throw unwrapTransportFailure(e);
    } catch (HttpStatusCodeException e) {
      throw toUnavailable(e);
    } catch (ResourceAccessException e) {
      throw new TransientOrderQueryException("Order service connection failed", e);
    } catch (CallNotPermittedException e) {
      throw new OrderQueryUnavailableException("Order service circuit breaker is open", e);
    } catch (Exception e) {
      throw new OrderQueryContractException("Order query response contract violated", e);
    }
  }

  /**
   * With the circuit breaker decorator on the HTTP service group, transport failures surface
   * wrapped in {@link NoFallbackAvailableException}; unwrap them so the retry policy sees the
   * original failure kind.
   */
  private static OrderQueryUnavailableException unwrapTransportFailure(
      NoFallbackAvailableException e) {
    Throwable cause = e.getCause() == null ? e : e.getCause();
    if (cause instanceof CallNotPermittedException) {
      return new OrderQueryUnavailableException("Order service circuit breaker is open", cause);
    }
    if (cause instanceof HttpStatusCodeException http) {
      return toUnavailable(http);
    }
    if (cause instanceof ResourceAccessException io) {
      return new TransientOrderQueryException("Order service connection failed", io);
    }
    return new OrderQueryContractException("Order query response contract violated", cause);
  }

  private static OrderQueryUnavailableException toUnavailable(HttpStatusCodeException e) {
    return switch (e.getStatusCode().value()) {
      case 429, 502, 503, 504 ->
          new TransientOrderQueryException(
              "Order service transient failure: HTTP %s".formatted(e.getStatusCode()), e);
      default ->
          new OrderQueryContractException(
              "Unexpected order-svc response status: HTTP %s".formatted(e.getStatusCode()), e);
    };
  }

  public record OrderPageResponse(List<OrderItem> items) {}

  public record OrderItem(
      String orderNo,
      String status,
      BigDecimal payableTotal,
      String currency,
      LocalDateTime createdAt) {}
}
