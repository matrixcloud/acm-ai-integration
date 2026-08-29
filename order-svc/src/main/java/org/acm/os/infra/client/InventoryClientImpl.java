package org.acm.os.infra.client;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.acm.os.application.port.out.InsufficientInventoryException;
import org.acm.os.application.port.out.InventoryClient;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * In-memory Mock implementation of {@link InventoryClient} (design §11.1: "Mock 库存显式维护可售、
 * 预占和已扣减数量").
 *
 * <p>Registered only for the {@code demo} profile when {@code order.adapters.inventory=mock}.
 * State lives in memory only and insufficient stock fails explicitly.
 */
@Component
@Profile("demo")
@ConditionalOnProperty(name = "order.adapters.inventory", havingValue = "mock")
public class InventoryClientImpl implements InventoryClient {
  private static final int DEFAULT_STOCK = 100;
  private final ConcurrentHashMap<String, AtomicInteger> stock = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, List<InventoryItem>> reservations =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, String> reservationByKey = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, String> reservationKeyById = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, List<InventoryItem>> confirmed = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Boolean> completedOperations = new ConcurrentHashMap<>();
  private MockFailureRegistry failureRegistry = new MockFailureRegistry();

  @Autowired
  void setFailureRegistry(MockFailureRegistry failureRegistry) {
    this.failureRegistry = failureRegistry;
  }

  @Override
  public synchronized InventoryReservation reserve(
      String orderNo, List<InventoryItem> items, String idempotencyKey) {
    failureRegistry.check("inventory-reserve");
    String existingReservation = reservationByKey.get(idempotencyKey);
    if (existingReservation != null) {
      return new InventoryReservation(existingReservation);
    }
    for (InventoryItem item : items) {
      AtomicInteger available = stock.computeIfAbsent(item.skuId(), k -> new AtomicInteger(DEFAULT_STOCK));
      if (available.get() < item.quantity()) {
        throw new InsufficientInventoryException(
            "Insufficient stock for SKU '%s' (requested %d, available %d)"
                .formatted(item.skuId(), item.quantity(), available.get()));
      }
    }
    for (InventoryItem item : items) {
      stock.get(item.skuId()).addAndGet(-item.quantity());
    }
    String reservationId = UUID.randomUUID().toString();
    reservations.put(reservationId, List.copyOf(items));
    reservationByKey.put(idempotencyKey, reservationId);
    reservationKeyById.put(reservationId, idempotencyKey);
    return new InventoryReservation(reservationId);
  }

  @Override
  public synchronized void release(String reservationId, String idempotencyKey) {
    failureRegistry.check("inventory-release");
    String operationKey = "release:" + reservationId + ":" + idempotencyKey;
    if (completedOperations.putIfAbsent(operationKey, true) != null) {
      return;
    }
    List<InventoryItem> items = reservations.remove(reservationId);
    if (items == null) {
      completedOperations.remove(operationKey);
      throw new IllegalArgumentException("Unknown inventory reservation '%s'".formatted(reservationId));
    }
    for (InventoryItem item : items) {
      stock.get(item.skuId()).addAndGet(item.quantity());
    }
    removeReservationKey(reservationId);
  }

  @Override
  public synchronized void confirm(String reservationId, String idempotencyKey) {
    failureRegistry.check("inventory-confirm");
    String operationKey = "confirm:" + reservationId + ":" + idempotencyKey;
    if (completedOperations.putIfAbsent(operationKey, true) != null) {
      return;
    }
    List<InventoryItem> items = reservations.remove(reservationId);
    if (items == null) {
      completedOperations.remove(operationKey);
      throw new IllegalArgumentException("Unknown inventory reservation '%s'".formatted(reservationId));
    }
    confirmed.put(reservationId, items);
    removeReservationKey(reservationId);
  }

  @Override
  public synchronized void restore(
      String orderNo, List<InventoryItem> items, String idempotencyKey) {
    failureRegistry.check("inventory-restore");
    String operationKey = "restore:" + orderNo + ":" + idempotencyKey;
    if (completedOperations.putIfAbsent(operationKey, true) != null) {
      return;
    }
    for (InventoryItem item : items) {
      stock.computeIfAbsent(item.skuId(), key -> new AtomicInteger()).addAndGet(item.quantity());
    }
  }

  public synchronized void setStock(String skuId, int quantity) {
    if (quantity < 0) {
      throw new IllegalArgumentException("Inventory quantity must not be negative");
    }
    stock.put(skuId, new AtomicInteger(quantity));
  }

  private void removeReservationKey(String reservationId) {
    String reservationKey = reservationKeyById.remove(reservationId);
    if (reservationKey != null) {
      reservationByKey.remove(reservationKey, reservationId);
    }
  }
}
