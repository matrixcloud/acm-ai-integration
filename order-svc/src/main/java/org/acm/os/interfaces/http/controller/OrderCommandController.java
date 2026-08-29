package org.acm.os.interfaces.http.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.acm.os.application.port.in.RefundUseCase;
import org.acm.os.application.port.in.ShipmentUseCase;
import org.acm.os.interfaces.http.mapper.OrderResponseMapper;
import org.acm.os.interfaces.http.request.ReasonRequest;
import org.acm.os.interfaces.http.response.CreateOrderResponse;
import org.acm.os.interfaces.http.response.RefundResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders/{orderNo}")
@RequiredArgsConstructor
public class OrderCommandController {
  private final RefundUseCase refundUseCase;
  private final ShipmentUseCase shipmentUseCase;
  private final OrderResponseMapper responseMapper;

  @PostMapping("/cancel")
  public ResponseEntity<CreateOrderResponse> cancel(
      @PathVariable String orderNo,
      @Valid @RequestBody ReasonRequest request,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(
            responseMapper.toResponse(
                refundUseCase.cancel(orderNo, request.getReason(), idempotencyKey)));
  }

  @PostMapping("/refunds")
  public ResponseEntity<RefundResponse> requestRefund(
      @PathVariable String orderNo,
      @Valid @RequestBody ReasonRequest request,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(
            responseMapper.toRefundResponse(
                refundUseCase.requestRefund(orderNo, request.getReason(), idempotencyKey)));
  }

  @PostMapping("/shipments/{shipmentNo}/confirm-receipt")
  public CreateOrderResponse confirmReceipt(
      @PathVariable String orderNo,
      @PathVariable String shipmentNo,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    return responseMapper.toResponse(
        shipmentUseCase.confirmReceipt(orderNo, shipmentNo, idempotencyKey));
  }
}
