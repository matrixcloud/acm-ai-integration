package org.acm.os.interfaces.http.controller;

import lombok.RequiredArgsConstructor;
import org.acm.os.application.port.in.PaymentUseCase;
import org.acm.os.interfaces.http.mapper.OrderResponseMapper;
import org.acm.os.interfaces.http.response.PaymentResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders/{orderNo}/payments")
@RequiredArgsConstructor
public class PaymentController {
  private final PaymentUseCase paymentUseCase;
  private final OrderResponseMapper responseMapper;

  @PostMapping
  public ResponseEntity<PaymentResponse> create(
      @PathVariable String orderNo,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(responseMapper.toPaymentResponse(paymentUseCase.createPayment(orderNo, idempotencyKey)));
  }
}
