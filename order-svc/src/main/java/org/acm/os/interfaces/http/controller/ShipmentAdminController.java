package org.acm.os.interfaces.http.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.acm.os.application.port.in.ShipmentUseCase;
import org.acm.os.interfaces.http.mapper.OrderResponseMapper;
import org.acm.os.interfaces.http.request.CreateShipmentRequest;
import org.acm.os.interfaces.http.response.ShipmentResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/orders/{orderNo}/shipments")
@RequiredArgsConstructor
public class ShipmentAdminController {
  private final ShipmentUseCase shipmentUseCase;
  private final OrderResponseMapper responseMapper;

  @PostMapping
  public ResponseEntity<ShipmentResponse> create(
      @PathVariable String orderNo,
      @Valid @RequestBody CreateShipmentRequest request,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            responseMapper.toShipmentResponse(
                shipmentUseCase.createShipment(
                    orderNo,
                    request.getCarrierCode(),
                    request.getItems().stream()
                        .map(
                            item ->
                                new ShipmentUseCase.ShipmentLine(
                                    item.getOrderItemId(), item.getQuantity()))
                        .toList(),
                    idempotencyKey)));
  }
}
