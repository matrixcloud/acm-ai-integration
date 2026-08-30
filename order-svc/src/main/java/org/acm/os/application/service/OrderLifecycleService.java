package org.acm.os.application.service;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.acm.os.application.exception.PersistedRetryableFailureException;
import org.acm.os.application.exception.RetryableOperationException;
import org.acm.os.application.port.in.PaymentUseCase;
import org.acm.os.application.port.in.RefundUseCase;
import org.acm.os.application.port.in.ShipmentUseCase;
import org.acm.os.application.port.out.InventoryClient;
import org.acm.os.application.port.out.InventoryClient.InventoryItem;
import org.acm.os.application.port.out.LogisticsClient;
import org.acm.os.application.port.out.LogisticsClient.AddressSnapshot;
import org.acm.os.application.port.out.PaymentClient;
import org.acm.os.domain.order.Order;
import org.acm.os.domain.order.OrderNotFoundException;
import org.acm.os.domain.order.OrderRepository;
import org.acm.os.domain.order.OrderStatus;
import org.acm.os.domain.payment.Payment;
import org.acm.os.domain.payment.PaymentStatus;
import org.acm.os.domain.refund.Refund;
import org.acm.os.domain.shared.BusinessNumberGenerator;
import org.acm.os.domain.shipment.Shipment;
import org.acm.os.domain.shipment.ShipmentItem;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderLifecycleService implements PaymentUseCase, RefundUseCase, ShipmentUseCase {
  private final OrderRepository orderRepository;
  private final InventoryClient inventoryClient;
  private final PaymentClient paymentClient;
  private final LogisticsClient logisticsClient;
  private final IdempotencyService idempotencyService;

  @Override
  public Payment createPayment(String orderNo, String idempotencyKey) {
    return idempotencyService.execute(
        operation("create-payment", idempotencyKey, Map.of("orderNo", orderNo), Payment.class),
        () -> {
          Order order = order(orderNo);
          order.assertCanCreatePayment();
          PaymentClient.PaymentSession session =
              paymentClient.create(
                  orderNo, order.getPayableTotal(), order.getCurrency(), idempotencyKey);
          Payment payment = order.addPayment(session.paymentToken());
          orderRepository.saveAndFlush(order);
          return payment;
        });
  }

  @Override
  public Order succeedPayment(String paymentNo, String externalPaymentNo, String idempotencyKey) {
    return idempotencyService.execute(
        operation(
            "succeed-payment",
            idempotencyKey,
            Map.of("paymentNo", paymentNo, "externalPaymentNo", externalPaymentNo),
            Order.class),
        () -> {
          Order order = orderByPayment(paymentNo);
          Payment payment = order.payment(paymentNo);
          if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            order.markPaid(payment, externalPaymentNo);
            return initializeDetails(order);
          }
          order.claimPaymentNotification(payment, externalPaymentNo);
          try {
            orderRepository.saveAndFlush(order);
          } catch (DataIntegrityViolationException exception) {
            throw new org.acm.os.domain.order.OrderStateConflictException(
                "External payment number '%s' was already used".formatted(externalPaymentNo));
          }
          inventoryClient.confirm(
              order.getInventoryReservationId(), "payment-confirm:" + externalPaymentNo);
          order.markPaid(payment, externalPaymentNo);
          return initializeDetails(orderRepository.saveAndFlush(order));
        });
  }

  @Override
  public Order failPayment(String paymentNo, String idempotencyKey) {
    return idempotencyService.execute(
        operation("fail-payment", idempotencyKey, Map.of("paymentNo", paymentNo), Order.class),
        () -> {
          Order order = orderByPayment(paymentNo);
          order.markPaymentFailed(order.payment(paymentNo));
          return initializeDetails(orderRepository.saveAndFlush(order));
        });
  }

  @Override
  public Order cancel(String orderNo, String reason, String idempotencyKey) {
    return executeRetryable(
        operation(
            "cancel-order",
            idempotencyKey,
            Map.of("orderNo", orderNo, "reason", reason),
            Order.class),
        () -> {
          Order order = order(orderNo);
          if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            inventoryClient.release(
                order.getInventoryReservationId(), "cancel-pending:" + order.getOrderNo());
            order.cancelPending();
            return initializeDetails(orderRepository.saveAndFlush(order));
          }
          String refundNo =
              BusinessNumberGenerator.deterministic(
                  "REF", "cancel:" + order.getOrderNo() + ":" + idempotencyKey);
          Refund refund = order.startCancel(reason, refundNo);
          executeRefund(order, refund);
          return initializeDetails(orderRepository.saveAndFlush(order));
        });
  }

  @Override
  public Refund requestRefund(String orderNo, String reason, String idempotencyKey) {
    return idempotencyService.execute(
        operation(
            "request-refund",
            idempotencyKey,
            Map.of("orderNo", orderNo, "reason", reason),
            Refund.class),
        () -> {
          Order order = order(orderNo);
          Refund refund = order.requestRefund(reason);
          orderRepository.saveAndFlush(order);
          return refund;
        });
  }

  @Override
  public Refund approveRefund(
      String refundNo, String reviewer, String comment, String idempotencyKey) {
    return executeRetryable(
        operation(
            "approve-refund",
            idempotencyKey,
            Map.of("refundNo", refundNo, "reviewer", reviewer, "comment", comment),
            Refund.class),
        () -> {
          Order order = orderByRefund(refundNo);
          Refund refund = order.refund(refundNo);
          order.approveRefund(refund, reviewer, comment);
          executeRefund(order, refund);
          orderRepository.saveAndFlush(order);
          return refund;
        });
  }

  @Override
  public Refund rejectRefund(
      String refundNo, String reviewer, String comment, String idempotencyKey) {
    return idempotencyService.execute(
        operation(
            "reject-refund",
            idempotencyKey,
            Map.of("refundNo", refundNo, "reviewer", reviewer, "comment", comment),
            Refund.class),
        () -> {
          Order order = orderByRefund(refundNo);
          Refund refund = order.refund(refundNo);
          order.rejectRefund(refund, reviewer, comment);
          orderRepository.saveAndFlush(order);
          return refund;
        });
  }

  @Override
  public Refund retryRefund(String refundNo, String idempotencyKey) {
    return executeRetryable(
        operation("retry-refund", idempotencyKey, Map.of("refundNo", refundNo), Refund.class),
        () -> {
          Order order = orderByRefund(refundNo);
          Refund refund = order.refund(refundNo);
          order.retryRefund(refund);
          executeRefund(order, refund);
          orderRepository.saveAndFlush(order);
          return refund;
        });
  }

  @Override
  public Shipment createShipment(
      String orderNo, String carrierCode, List<ShipmentLine> lines, String idempotencyKey) {
    return idempotencyService.execute(
        operation(
            "create-shipment",
            idempotencyKey,
            Map.of("orderNo", orderNo, "carrierCode", carrierCode, "items", lines),
            Shipment.class),
        () -> {
          Order order = order(orderNo);
          List<ShipmentItem> domainItems =
              lines.stream()
                  .map(line -> ShipmentItem.of(line.orderItemId(), line.quantity()))
                  .toList();
          order.validateShipmentItems(domainItems);
          String shipmentNo = BusinessNumberGenerator.deterministic("SHP", idempotencyKey);
          LogisticsClient.LogisticsShipment external =
              logisticsClient.createShipment(
                  orderNo,
                  shipmentNo,
                  carrierCode,
                  address(order),
                  lines.stream()
                      .map(
                          line ->
                              new LogisticsClient.ShipmentItem(line.orderItemId(), line.quantity()))
                      .toList(),
                  idempotencyKey);
          Shipment shipment =
              Shipment.create(shipmentNo, carrierCode, external.trackingNo(), domainItems);
          order.allocateShipment(shipment);
          orderRepository.saveAndFlush(order);
          return shipment;
        });
  }

  @Override
  public Order confirmReceipt(String orderNo, String shipmentNo, String idempotencyKey) {
    return idempotencyService.execute(
        operation(
            "confirm-receipt",
            idempotencyKey,
            Map.of("orderNo", orderNo, "shipmentNo", shipmentNo),
            Order.class),
        () -> {
          Order order = order(orderNo);
          Shipment shipment = order.shipment(shipmentNo);
          if (shipment.getStatus() != org.acm.os.domain.shipment.ShipmentStatus.DELIVERED) {
            logisticsClient.confirmReceipt(
                shipment.getTrackingNo(), "confirm-receipt:" + shipment.getShipmentNo());
          }
          order.confirmShipmentDelivered(shipmentNo);
          return initializeDetails(orderRepository.saveAndFlush(order));
        });
  }

  private void executeRefund(Order order, Refund refund) {
    try {
      if (!refund.isPaymentRefunded()) {
        Payment payment =
            order.getPayments().stream()
                .filter(value -> value.getStatus() == PaymentStatus.SUCCEEDED)
                .findFirst()
                .orElseThrow();
        PaymentClient.ExternalRefund external =
            paymentClient.refund(
                payment.getPaymentNo(),
                refund.getAmount(),
                refund.getCurrency(),
                "refund:" + refund.getRefundNo() + ":payment");
        refund.markPaymentRefunded(external.externalRefundNo());
      }
      if (!refund.isInventoryRestored()) {
        inventoryClient.restore(
            order.getOrderNo(),
            inventoryItems(order),
            "refund:" + refund.getRefundNo() + ":inventory");
        refund.markInventoryRestored();
      }
      order.completeRefund(refund);
    } catch (RuntimeException exception) {
      order.failRefund(refund);
      orderRepository.saveAndFlush(order);
      throw new PersistedRetryableFailureException(exception);
    }
  }

  private Order order(String orderNo) {
    return orderRepository
        .findByOrderNoForUpdate(orderNo)
        .orElseThrow(() -> new OrderNotFoundException(orderNo));
  }

  private Order orderByPayment(String paymentNo) {
    return orderRepository
        .findByPaymentNoForUpdate(paymentNo)
        .orElseThrow(() -> new OrderNotFoundException("payment:" + paymentNo));
  }

  private Order orderByRefund(String refundNo) {
    return orderRepository
        .findByRefundNoForUpdate(refundNo)
        .orElseThrow(() -> new OrderNotFoundException("refund:" + refundNo));
  }

  private static List<InventoryItem> inventoryItems(Order order) {
    return order.getItems().stream()
        .map(item -> new InventoryItem(item.getSkuId(), item.getQuantity()))
        .toList();
  }

  private static AddressSnapshot address(Order order) {
    return new AddressSnapshot(
        order.getRecipientName(),
        order.getRecipientPhone(),
        order.getProvince(),
        order.getCity(),
        order.getDistrict(),
        order.getDetailAddress());
  }

  private static <R> IdempotencyService.IdempotentOperation<R> operation(
      String name, String key, Object request, Class<R> responseType) {
    return new IdempotencyService.IdempotentOperation<>(name, key, request, responseType);
  }

  private static Order initializeDetails(Order order) {
    order.getItems().size();
    order.getPayments().size();
    order.getRefunds().size();
    order.getShipments().forEach(shipment -> shipment.getItems().size());
    return order;
  }

  private <R> R executeRetryable(
      IdempotencyService.IdempotentOperation<R> operation, java.util.function.Supplier<R> action) {
    try {
      return idempotencyService.executeRetryable(operation, action);
    } catch (RetryableOperationException exception) {
      throw exception.original();
    }
  }
}
