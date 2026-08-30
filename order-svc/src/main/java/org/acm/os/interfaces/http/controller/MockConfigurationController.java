package org.acm.os.interfaces.http.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.acm.os.application.port.in.MockConfigurationUseCase;
import org.acm.os.interfaces.http.request.SetInventoryRequest;
import org.acm.os.interfaces.http.request.SetProductRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("demo")
@RequestMapping("/mock")
@RequiredArgsConstructor
public class MockConfigurationController {
  private final MockConfigurationUseCase mockConfiguration;

  @PutMapping("/products/{skuId}")
  public ResponseEntity<Void> setProduct(
      @PathVariable String skuId,
      @Valid @RequestBody SetProductRequest request,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    mockConfiguration.setProduct(
        skuId,
        request.getProductName(),
        request.getUnitPrice(),
        request.getCurrency(),
        request.getSaleable(),
        idempotencyKey);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/inventory/{skuId}")
  public ResponseEntity<Void> setInventory(
      @PathVariable String skuId,
      @Valid @RequestBody SetInventoryRequest request,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    mockConfiguration.setInventory(skuId, request.getQuantity(), idempotencyKey);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/failures/{capability}")
  public ResponseEntity<Void> failNext(
      @PathVariable String capability, @RequestHeader("Idempotency-Key") String idempotencyKey) {
    mockConfiguration.failNext(capability, idempotencyKey);
    return ResponseEntity.noContent().build();
  }
}
