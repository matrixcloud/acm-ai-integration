package org.acm.os.interfaces.http.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.acm.os.application.port.in.RefundUseCase;
import org.acm.os.interfaces.http.mapper.OrderResponseMapper;
import org.acm.os.interfaces.http.request.ReviewRefundRequest;
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
@RequestMapping("/admin/refunds/{refundNo}")
@RequiredArgsConstructor
public class RefundAdminController {
  private final RefundUseCase refundUseCase;
  private final OrderResponseMapper responseMapper;

  @PostMapping("/approve")
  public ResponseEntity<RefundResponse> approve(
      @PathVariable String refundNo,
      @Valid @RequestBody ReviewRefundRequest request,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(
            responseMapper.toRefundResponse(
                refundUseCase.approveRefund(
                    refundNo, request.getReviewer(), request.getComment(), idempotencyKey)));
  }

  @PostMapping("/reject")
  public RefundResponse reject(
      @PathVariable String refundNo,
      @Valid @RequestBody ReviewRefundRequest request,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    return responseMapper.toRefundResponse(
        refundUseCase.rejectRefund(
            refundNo, request.getReviewer(), request.getComment(), idempotencyKey));
  }

  @PostMapping("/retry")
  public ResponseEntity<RefundResponse> retry(
      @PathVariable String refundNo,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(responseMapper.toRefundResponse(refundUseCase.retryRefund(refundNo, idempotencyKey)));
  }
}
