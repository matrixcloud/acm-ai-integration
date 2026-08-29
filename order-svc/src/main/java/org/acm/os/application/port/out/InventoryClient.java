package org.acm.os.application.port.out;

import java.util.List;

/**
 * Port for the external inventory service (design §5.4).
 *
 * <p>Implemented by a Mock adapter under the {@code demo} profile; a real adapter must be
 * configured for non-demo deployment.
 */
public interface InventoryClient {
  /**
   * Reserves stock for the given order items.
   *
   * @param orderNo order being created
   * @param items quantities per SKU to reserve
   * @param idempotencyKey key identifying this reserve attempt; must be stable across retries of
   *     the same attempt (callers may use the order number — the outer idempotency protocol
   *     already guarantees one execution per client key)
   * @return reservation id to be stored on the order
   * @throws InsufficientInventoryException if any SKU has insufficient stock
   */
  InventoryReservation reserve(String orderNo, List<InventoryItem> items, String idempotencyKey);

  /**
   * Releases a previously made reservation, restoring the reserved quantities (design §8.1
   * compensation: reservation succeeded but order persistence failed).
   *
   * @param reservationId id returned by {@link #reserve}
   * @param idempotencyKey idempotency key from the originating request
   * @throws IllegalArgumentException if the reservation id is unknown
   */
  void release(String reservationId, String idempotencyKey);

  /** Immutable inventory item for a reservation request. */
  record InventoryItem(String skuId, Integer quantity) {}

  /** Immutable inventory reservation result. */
  record InventoryReservation(String reservationId) {}
}
