package org.acm.os.domain.order;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

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
  @EntityGraph(attributePaths = "items")
  Optional<Order> findByOrderNo(String orderNo);

  Optional<Order> findByPaymentsPaymentNo(String paymentNo);

  Optional<Order> findByRefundsRefundNo(String refundNo);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select order from Order order where order.orderNo = :orderNo")
  Optional<Order> findByOrderNoForUpdate(@Param("orderNo") String orderNo);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select order from Order order join order.payments payment "
          + "where payment.paymentNo = :paymentNo")
  Optional<Order> findByPaymentNoForUpdate(@Param("paymentNo") String paymentNo);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select order from Order order join order.refunds refund "
          + "where refund.refundNo = :refundNo")
  Optional<Order> findByRefundNoForUpdate(@Param("refundNo") String refundNo);

  /** Fetches order summaries without collection joins so pagination remains database-backed. */
  Page<Order> findByCustomerId(String customerId, Pageable pageable);

  Page<Order> findByCustomerIdAndStatus(String customerId, OrderStatus status, Pageable pageable);
}
