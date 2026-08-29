package org.acm.os.domain.order;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository port for the {@link Order} aggregate.
 *
 * <p>Defined in the domain layer; implemented by Spring Data JPA. The aggregate is persisted as a
 * whole (order + order items) within a single transaction via {@link JpaRepository#save(Object)
 * save}.
 */
public interface OrderRepository extends JpaRepository<Order, Long> {

  /**
   * @param orderNo system-generated order number
   * @return the order with its items, or empty if not found
   */
  Optional<Order> findByOrderNo(String orderNo);

  /** Fetches orders with their items so adapters can map outside a persistence session. */
  @EntityGraph(attributePaths = "items")
  Page<Order> findByCustomerId(String customerId, Pageable pageable);

  @EntityGraph(attributePaths = "items")
  Page<Order> findByCustomerIdAndStatus(String customerId, OrderStatus status, Pageable pageable);
}
