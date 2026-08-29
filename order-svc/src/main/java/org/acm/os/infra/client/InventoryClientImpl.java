package org.acm.os.infra.client;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.acm.os.application.port.out.InsufficientInventoryException;
import org.acm.os.application.port.out.InventoryClient;
import org.springframework.stereotype.Component;

/**
 * In-memory Mock implementation of {@link InventoryClient} (design §11.1: "Mock 库存显式维护可售、
 * 预占和已扣减数量").
 *
 * <p>Always registered — there is no profile gate. State lives in memory only, so this adapter is
 * not suitable for production: replace it with a real client before any non-demo deployment.
 * Insufficient stock throws explicitly — no silent fallback.
 */
@Component
public class InventoryClientImpl implements InventoryClient {
  private static final int DEFAULT_STOCK = 100;
  private final ConcurrentHashMap<String, AtomicInteger> stock = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, List<InventoryItem>> reservations =
      new ConcurrentHashMap<>();

  @Override
  public InventoryReservation reserve(String orderNo, List<InventoryItem> items, String idempotencyKey) {
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
    return new InventoryReservation(reservationId);
  }

  @Override
  public void release(String reservationId, String idempotencyKey) {
    List<InventoryItem> items = reservations.remove(reservationId);
    if (items == null) {
      throw new IllegalArgumentException("Unknown inventory reservation '%s'".formatted(reservationId));
    }
    for (InventoryItem item : items) {
      stock.get(item.skuId()).addAndGet(item.quantity());
    }
  }
}
