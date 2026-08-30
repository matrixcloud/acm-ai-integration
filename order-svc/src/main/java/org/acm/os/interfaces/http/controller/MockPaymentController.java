package org.acm.os.interfaces.http.controller;

import lombok.RequiredArgsConstructor;
import org.acm.os.application.port.in.PaymentUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mock/payments/{paymentNo}")
@RequiredArgsConstructor
public class MockPaymentController {
  private final PaymentUseCase paymentUseCase;

  @PostMapping("/succeed")
  public ResponseEntity<Void> succeed(
      @PathVariable String paymentNo, @RequestHeader("Idempotency-Key") String idempotencyKey) {
    paymentUseCase.succeedPayment(paymentNo, "mock-external-" + paymentNo, idempotencyKey);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/fail")
  public ResponseEntity<Void> fail(
      @PathVariable String paymentNo, @RequestHeader("Idempotency-Key") String idempotencyKey) {
    paymentUseCase.failPayment(paymentNo, idempotencyKey);
    return ResponseEntity.noContent().build();
  }
}
