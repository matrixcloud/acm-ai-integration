package org.acm.os.application.port.in;

import jakarta.validation.Valid;
import org.acm.os.application.port.in.command.CreateOrderCommand;
import org.acm.os.application.port.in.query.SearchOrderQuery;
import org.acm.os.domain.order.Order;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;

/**
 * Application port for order use cases.
 *
 * <p>Returns domain {@link Order} instances; the adapter layer is responsible for mapping to HTTP
 * responses. This keeps the application layer unaware of transport DTOs.
 */
@Validated
public interface OrderUseCase {
  /**
   * Creates a new order, guarded by an idempotency key.
   *
   * <p>Same key + same request replays the cached {@link Order}; same key + different request
   * rejects; a key held by a concurrent writer rejects.
   *
   * @param command validated create-order input
   * @param idempotencyKey client-provided idempotency key
   * @return the persisted order in {@link org.acm.os.domain.order.OrderStatus#PENDING_PAYMENT}
   */
  Order create(@Valid CreateOrderCommand command, String idempotencyKey);

  /**
   * Searches orders by customer.
   *
   * @param query validated search query
   * @return a page of matching orders
   */
  Page<Order> search(@Valid SearchOrderQuery query);

  Order get(String orderNo);
}
