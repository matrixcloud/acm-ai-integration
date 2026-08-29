package org.acm.os.interfaces.http.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.acm.common.http.PageResponse;
import org.acm.os.application.port.in.command.CreateOrderCommand;
import org.acm.os.application.port.in.OrderUseCase;
import org.acm.os.domain.order.Order;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.acm.os.interfaces.http.mapper.OrderRequestMapper;
import org.acm.os.interfaces.http.mapper.OrderResponseMapper;
import org.acm.os.interfaces.http.request.CreateOrderRequest;
import org.acm.os.interfaces.http.request.SearchOrderRequest;
import org.acm.os.interfaces.http.response.CreateOrderResponse;

/**
 * REST adapter for order use cases.
 *
 * <p>The adapter is intentionally thin: it only translates between HTTP DTOs and application
 * commands/results. Idempotency is owned by the application layer ({@link OrderUseCase#create}),
 * not orchestrated here.
 */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

  private final OrderUseCase orderService;
  private final OrderRequestMapper requestMapper;
  private final OrderResponseMapper responseMapper;

  /**
   * Creates a new order.
   *
   * <p>Idempotency is handled inside the use case: same {@code Idempotency-Key} + same request
   * replays the cached order; a key reused with a different body yields 409; a key concurrently
   * held by an in-flight writer yields 409.
   *
   * @param request validated request body
   * @param idempotencyKey client-provided idempotency key
   * @return 201 Created with order detail
   */
  @PostMapping
  public ResponseEntity<CreateOrderResponse> create(
      @Valid @RequestBody CreateOrderRequest request,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    CreateOrderCommand command = requestMapper.toCommand(request);
    Order order = orderService.create(command, idempotencyKey);
    return ResponseEntity.status(HttpStatus.CREATED).body(responseMapper.toResponse(order));
  }

  /**
   * Searches orders by customer.
   *
   * @param request validated query parameters
   * @return 200 with a page of order summaries
   */
  @GetMapping
  public PageResponse<CreateOrderResponse> search(@Valid SearchOrderRequest request) {
    Page<Order> result = orderService.search(requestMapper.toQuery(request));
    return new PageResponse<>(
        responseMapper.toResponseList(result.getContent()),
        new PageResponse.Page(
            result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()));
  }
}
