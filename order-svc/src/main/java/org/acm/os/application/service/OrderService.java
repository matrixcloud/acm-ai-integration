package org.acm.os.application.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.acm.os.application.port.in.OrderUseCase;
import org.acm.os.application.port.in.command.CreateOrderCommand;
import org.acm.os.application.port.in.query.SearchOrderQuery;
import org.acm.os.application.port.out.InventoryClient;
import org.acm.os.application.port.out.InventoryClient.InventoryItem;
import org.acm.os.application.port.out.ProductCatalogClient;
import org.acm.os.application.port.out.ProductCatalogClient.ProductSnapshot;
import org.acm.os.domain.order.CurrencyMismatchException;
import org.acm.os.domain.order.DuplicateSkuException;
import org.acm.os.domain.order.Order;
import org.acm.os.domain.order.OrderItem;
import org.acm.os.domain.order.OrderRepository;
import org.acm.os.domain.shared.InvalidRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link OrderUseCase} implementation.
 *
 * <p>{@link #create} is guarded by {@link IdempotencyService}: same key + same request replays
 * the cached {@link Order}; the idempotency guard record shares one transaction with the business
 * action, so a failed attempt rolls back completely.
 *
 * <p>create-order workflow (design §8.1):
 *
 * <ol>
 *   <li>map command lines to domain {@link OrderItem}s, enriching each with the
 *       server-authoritative price from {@link ProductCatalogClient}
 *   <li>build the {@link Order} aggregate via its factory (enforces invariants + derives totals)
 *   <li>reserve inventory via {@link InventoryClient#reserve}
 *   <li>persist; JPA cascades items
 * </ol>
 *
 * <p>If inventory reserve succeeded but the DB save fails, the reservation is compensated with
 * {@link InventoryClient#release} before the failure propagates (design §8.1).
 */
@Service
@RequiredArgsConstructor
public class OrderService implements OrderUseCase {
  private static final Logger log = LoggerFactory.getLogger(OrderService.class);

  private static final Set<String> SORTABLE_FIELDS =
      Set.of("createdAt", "updatedAt", "orderNo", "payableTotal", "status");
  private static final String DEFAULT_SORT_FIELD = "createdAt";
  private static final Sort.Direction DEFAULT_SORT_DIRECTION = Sort.Direction.DESC;
  static final String CREATE_OPERATION = "create-order";

  private final OrderRepository orderRepository;
  private final ProductCatalogClient productCatalogClient;
  private final InventoryClient inventoryClient;
  private final IdempotencyService idempotencyService;

  @Override
  public Order create(CreateOrderCommand command, String idempotencyKey) {
    IdempotencyService.IdempotentOperation<Order> operation =
        new IdempotencyService.IdempotentOperation<>(
            CREATE_OPERATION, idempotencyKey, command, Order.class);
    return idempotencyService.execute(operation, () -> createInternal(command, idempotencyKey));
  }

  private Order createInternal(CreateOrderCommand command, String idempotencyKey) {
    List<OrderItem> items = enrichOrderItems(command.getCurrency(), command.getItems());
    Order order =
        Order.create(
            command.getCustomerId(),
            command.getCurrency(),
            command.getRecipientName(),
            command.getRecipientPhone(),
            command.getProvince(),
            command.getCity(),
            command.getDistrict(),
            command.getDetailAddress(),
            items);
    String orderNo = order.getOrderNo();

    List<InventoryItem> inventoryItems =
        order.getItems().stream()
            .map(item -> new InventoryItem(item.getSkuId(), item.getQuantity()))
            .toList();
    String reservationId =
        inventoryClient.reserve(orderNo, inventoryItems, idempotencyKey).reservationId();
    order.setInventoryReservationId(reservationId);

    try {
      // saveAndFlush so persistence failures surface inside this method, where the
      // reservation can still be compensated.
      return orderRepository.saveAndFlush(order);
    } catch (RuntimeException e) {
      try {
        inventoryClient.release(reservationId, idempotencyKey);
      } catch (RuntimeException releaseFailure) {
        // Never mask the original persistence failure with a compensation failure.
        log.error(
            "Failed to release inventory reservation '{}' for order {}", reservationId, orderNo, releaseFailure);
        e.addSuppressed(releaseFailure);
      }
      throw e;
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Page<Order> search(SearchOrderQuery query) {
    PageRequest pageRequest =
        PageRequest.of(query.getPage() - 1, query.getSize(), buildSort(query));
    return query.getStatus() == null
            ? orderRepository.findByCustomerId(query.getCustomerId(), pageRequest)
            : orderRepository.findByCustomerIdAndStatus(
                query.getCustomerId(), query.getStatus(), pageRequest);
  }

  /**
   * Builds the sort for a search: whitelisted field, {@code DESC} by {@code createdAt} when the
   * client omits the sort. Unknown fields or directions are rejected — never silently ignored.
   */
  private static Sort buildSort(SearchOrderQuery query) {
    String field = query.getSortBy() == null ? DEFAULT_SORT_FIELD : query.getSortBy();
    if (!SORTABLE_FIELDS.contains(field)) {
      throw new InvalidRequestException("Unsupported sort field '%s'".formatted(field));
    }
    Sort.Direction direction =
        query.getDirection() == null
            ? DEFAULT_SORT_DIRECTION
            : parseDirection(query.getDirection());
    return Sort.by(direction, field);
  }

  private static Sort.Direction parseDirection(String direction) {
    try {
      return Sort.Direction.fromString(direction);
    } catch (IllegalArgumentException e) {
      throw new InvalidRequestException(
          "Unsupported sort direction '%s'".formatted(direction));
    }
  }

  /**
   * Builds domain {@link OrderItem}s from the command's lines, enriching each with the catalog
   * snapshot (name, unit price, currency).
   *
   * <p>Pricing authority is the server (design §6.2), so item construction merges command
   * quantities with catalog snapshots. {@code lineNo} and {@code lineAmount} are intentionally
   * left unset — they are derived and set by {@link Order#replaceItems(List)}.
   *
   * @throws DuplicateSkuException if the command contains repeated SKU IDs
   * @throws CurrencyMismatchException if an item's currency differs from the order currency
   */
  private List<OrderItem> enrichOrderItems(
      String currency, List<CreateOrderCommand.OrderLine> orderLines) {
    Set<String> skuIds =
        orderLines.stream().map(CreateOrderCommand.OrderLine::getSkuId).collect(Collectors.toSet());
    if (skuIds.size() != orderLines.size()) {
      // Fail before any external call (design §12.1: domain validation precedes external calls);
      // the aggregate re-checks this as an invariant guard.
      throw new DuplicateSkuException("Order contains duplicate SKU IDs");
    }
    List<ProductSnapshot> snapshots = productCatalogClient.getSaleableProducts(skuIds);
    Map<String, ProductSnapshot> bySku =
        snapshots.stream().collect(Collectors.toMap(ProductSnapshot::skuId, Function.identity()));

    return orderLines.stream()
        .map(
            line -> {
              ProductSnapshot snapshot = bySku.get(line.getSkuId());
              if (snapshot == null) {
                // getSaleableProducts already threw for unknown SKUs; defensive guard.
                throw new IllegalStateException(
                    "No snapshot for SKU '%s'".formatted(line.getSkuId()));
              }
              if (!snapshot.currency().equals(currency)) {
                throw new CurrencyMismatchException(
                    "Currency mismatch for SKU '%s': order %s but product %s"
                        .formatted(line.getSkuId(), currency, snapshot.currency()));
              }
              OrderItem item = new OrderItem();
              item.setSkuId(snapshot.skuId());
              item.setProductName(snapshot.productName());
              item.setUnitPrice(snapshot.unitPrice());
              item.setQuantity(line.getQuantity());
              return item;
            })
        .toList();
  }
}
