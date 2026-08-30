package org.acm.ca.infra.client;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
      List<OrderSummary> orders = toSummaries(response);
      log.info(
          "http.out service=order-svc op=recent-orders customerId={} orders={} durationMs={}",
          customerId,
          orders.size(),
          (System.nanoTime() - start) / 1_000_000);
      return orders;
    } catch (Exception e) {
      throw translateFailure(e);
    }
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
  public List<OrderSummary> findByRecipientPhone(String recipientPhone) {
    long start = System.nanoTime();
    try {
      OrderPageResponse response =
          orderServiceHttpClient.searchByPhone(
              API_VERSION,
              recipientPhone,
              RECENT_ORDER_PAGE,
              RECENT_ORDER_SIZE,
              SORT_FIELD_CREATED_AT,
              SORT_DIRECTION_DESC);
      List<OrderSummary> orders = toSummaries(response);
      log.info(
          "http.out service=order-svc op=search-by-phone recipientPhone={} orders={} durationMs={}",
          recipientPhone,
          orders.size(),
          (System.nanoTime() - start) / 1_000_000);
      return orders;
    } catch (Exception e) {
      throw translateFailure(e);
    }
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
  public Optional<OrderDetail> findByOrderNo(String orderNo) {
    long start = System.nanoTime();
    try {
      OrderDetailResponse response = orderServiceHttpClient.getOrder(API_VERSION, orderNo);
      if (response == null) {
        throw new OrderQueryContractException("Order detail response body must not be null");
      }
      OrderDetail detail = toDetail(response);
      log.info(
          "http.out service=order-svc op=get-order orderNo={} status={} durationMs={}",
          orderNo,
          detail.status(),
          (System.nanoTime() - start) / 1_000_000);
      return Optional.of(detail);
    } catch (Exception e) {
      if (isNotFound(e)) {
        return Optional.empty();
      }
      throw translateFailure(e);
    }
  }

  private static List<OrderSummary> toSummaries(OrderPageResponse response) {
    if (response == null) {
      throw new OrderQueryContractException("Order query response body must not be null");
    }
    if (response.items() == null) {
      throw new OrderQueryContractException("Order query response items must not be null");
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
  }

  private static OrderDetail toDetail(OrderDetailResponse response) {
    List<DetailItem> items =
        response.items() == null
            ? List.of()
            : response.items().stream()
                .map(item -> new DetailItem(item.productName(), item.quantity(), item.unitPrice()))
                .toList();
    List<DetailShipment> shipments =
        response.shipments() == null
            ? List.of()
            : response.shipments().stream()
                .map(
                    shipment ->
                        new DetailShipment(
                            shipment.shipmentNo(), shipment.carrierCode(), shipment.status()))
                .toList();
    return new OrderDetail(
        response.orderNo(),
        response.customerId(),
        response.status(),
        response.currency(),
        response.itemTotal(),
        response.payableTotal(),
        items,
        shipments);
  }

  /**
   * Maps raw client failures to the {@link OrderQueryUnavailableException} hierarchy, mirroring
   * {@code getRecentOrders}'s historical behavior: the circuit breaker decorator wraps transport
   * failures in {@link NoFallbackAvailableException}, so the original kind must be unwrapped before
   * the retry policy can see it.
   */
  private static OrderQueryUnavailableException translateFailure(Exception e) {
    if (e instanceof OrderQueryUnavailableException unavailable) {
      return unavailable;
    }
    if (e instanceof NoFallbackAvailableException noFallback) {
      return unwrapTransportFailure(noFallback);
    }
    if (e instanceof HttpStatusCodeException http) {
      return toUnavailable(http);
    }
    if (e instanceof ResourceAccessException io) {
      return new TransientOrderQueryException("Order service connection failed", io);
    }
    if (e instanceof CallNotPermittedException notPermitted) {
      return new OrderQueryUnavailableException(
          "Order service circuit breaker is open", notPermitted);
    }
    return new OrderQueryContractException("Order query response contract violated", e);
  }

  private static boolean isNotFound(Throwable e) {
    Throwable cause = e;
    while (cause != null) {
      if (cause instanceof HttpStatusCodeException http && http.getStatusCode().value() == 404) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }

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

  public record OrderDetailResponse(
      String orderNo,
      String customerId,
      String status,
      String currency,
      BigDecimal itemTotal,
      BigDecimal payableTotal,
      List<Item> items,
      List<Shipment> shipments) {

    public record Item(String productName, int quantity, BigDecimal unitPrice) {}

    public record Shipment(String shipmentNo, String carrierCode, String status) {}
  }
}
