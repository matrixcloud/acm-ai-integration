package org.acm.os.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.acm.os.application.exception.PersistedRetryableFailureException;
import org.acm.os.application.exception.RetryableOperationException;
import org.acm.os.application.port.in.ShipmentUseCase.ShipmentLine;
import org.acm.os.application.port.out.InventoryClient;
import org.acm.os.application.port.out.LogisticsClient;
import org.acm.os.application.port.out.LogisticsClient.AddressSnapshot;
import org.acm.os.application.port.out.PaymentClient;
import org.acm.os.domain.order.Order;
import org.acm.os.domain.order.OrderItem;
import org.acm.os.domain.order.OrderNotFoundException;
import org.acm.os.domain.order.OrderRepository;
import org.acm.os.domain.order.OrderStatus;
import org.acm.os.domain.payment.Payment;
import org.acm.os.domain.payment.PaymentStatus;
import org.acm.os.domain.refund.Refund;
import org.acm.os.domain.refund.RefundStatus;
import org.acm.os.domain.shipment.Shipment;
import org.acm.os.domain.shipment.ShipmentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Lifecycle orchestration tests built on real domain aggregates: only the outbound ports are
 * mocked, so assertions verify actual state transitions rather than delegation to mocked entities.
 */
@ExtendWith(MockitoExtension.class)
class OrderLifecycleServiceTest {
  @Mock private OrderRepository orderRepository;
  @Mock private InventoryClient inventoryClient;
  @Mock private PaymentClient paymentClient;
  @Mock private LogisticsClient logisticsClient;
  @Mock private IdempotencyService idempotencyService;

  private OrderLifecycleService service;

  @BeforeEach
  void setUp() {
    service =
        new OrderLifecycleService(
            orderRepository, inventoryClient, paymentClient, logisticsClient, idempotencyService);
    // Idempotency pass-through: this suite exercises lifecycle orchestration, not idempotency.
    lenient()
        .when(idempotencyService.execute(any(), any()))
        .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
    lenient()
        .when(idempotencyService.executeRetryable(any(), any()))
        .thenAnswer(
            invocation -> {
              try {
                return ((Supplier<?>) invocation.getArgument(1)).get();
              } catch (PersistedRetryableFailureException exception) {
                throw new RetryableOperationException(exception.original());
              }
            });
    lenient()
        .when(orderRepository.saveAndFlush(any(Order.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void createPaymentUsesOrderAmountAndAttachesCreatedPayment() {
    Order order = order();
    givenOrder(order);
    when(paymentClient.create(order.getOrderNo(), new BigDecimal("99.00"), "CNY", "key"))
        .thenReturn(new PaymentClient.PaymentSession("token"));

    Payment payment = service.createPayment(order.getOrderNo(), "key");

    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CREATED);
    assertThat(payment.getAmount()).isEqualByComparingTo("99.00");
    assertThat(order.getPayments()).containsExactly(payment);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    verify(orderRepository).saveAndFlush(order);
  }

  @Test
  void succeedPaymentConfirmsInventoryAndMarksOrderPaid() {
    Order order = order();
    Payment payment = order.addPayment("token");
    givenOrderByPayment(order, payment);

    Order result = service.succeedPayment(payment.getPaymentNo(), "EXT-1", "key");

    assertThat(result).isSameAs(order);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    assertThat(payment.getExternalPaymentNo()).isEqualTo("EXT-1");
    verify(inventoryClient).confirm("reservation", "payment-confirm:EXT-1");
  }

  @Test
  void duplicateSuccessfulPaymentNotificationSkipsInventoryConfirm() {
    Order order = paidOrder();
    Payment payment = order.getPayments().get(0);
    givenOrderByPayment(order, payment);

    service.succeedPayment(payment.getPaymentNo(), "external-payment", "key");

    verify(inventoryClient, never()).confirm(any(), any());
    assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
  }

  @Test
  void failPaymentMarksPaymentFailed() {
    Order order = order();
    Payment payment = order.addPayment("token");
    givenOrderByPayment(order, payment);

    service.failPayment(payment.getPaymentNo(), "key");

    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    verify(orderRepository).saveAndFlush(order);
  }

  @Test
  void cancelPendingOrderReleasesInventoryReservation() {
    Order order = order();
    givenOrder(order);

    Order result = service.cancel(order.getOrderNo(), "reason", "key");

    assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELED);
    assertThat(order.getRefunds()).isEmpty();
    verify(inventoryClient).release("reservation", "cancel-pending:" + order.getOrderNo());
  }

  @Test
  void cancelPaidOrderExecutesRefundWithBothExternalSteps() {
    Order order = paidOrder();
    givenOrder(order);
    when(paymentClient.refund(any(), any(), any(), any()))
        .thenReturn(new PaymentClient.ExternalRefund("EXT-REF"));

    Order result = service.cancel(order.getOrderNo(), "cancel reason", "key");

    assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELED);
    Refund refund = order.getRefunds().get(0);
    assertThat(refund.getStatus()).isEqualTo(RefundStatus.SUCCEEDED);
    assertThat(refund.isPaymentRefunded()).isTrue();
    assertThat(refund.isInventoryRestored()).isTrue();
    verify(paymentClient)
        .refund(
            order.getPayments().get(0).getPaymentNo(),
            new BigDecimal("99.00"),
            "CNY",
            "refund:" + refund.getRefundNo() + ":payment");
    verify(inventoryClient)
        .restore(
            eq(order.getOrderNo()), anyList(), eq("refund:" + refund.getRefundNo() + ":inventory"));
  }

  @Test
  void requestRefundPutsOrderIntoReviewAndRejectReturnsItToPaid() {
    Order order = paidOrder();
    givenOrder(order);

    Refund requested = service.requestRefund(order.getOrderNo(), "changed mind", "key");

    assertThat(requested.getStatus()).isEqualTo(RefundStatus.PENDING_REVIEW);
    assertThat(order.getRefunds()).containsExactly(requested);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUND_REVIEW);

    givenOrderByRefund(order, requested);
    Refund rejected = service.rejectRefund(requested.getRefundNo(), "admin", "no", "key-2");

    assertThat(rejected).isSameAs(requested);
    assertThat(rejected.getStatus()).isEqualTo(RefundStatus.REJECTED);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
  }

  @Test
  void approveRefundExecutesBothExternalStepsAndCompletesRefund() {
    Order order = paidOrder();
    givenOrder(order);
    Refund requested = service.requestRefund(order.getOrderNo(), "refund", "key-1");
    givenOrderByRefund(order, requested);
    when(paymentClient.refund(any(), any(), any(), any()))
        .thenReturn(new PaymentClient.ExternalRefund("EXT-REF"));

    Refund approved = service.approveRefund(requested.getRefundNo(), "admin", "yes", "key-2");

    assertThat(approved).isSameAs(requested);
    assertThat(approved.getStatus()).isEqualTo(RefundStatus.SUCCEEDED);
    assertThat(approved.getExternalRefundNo()).isEqualTo("EXT-REF");
    assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
    verify(paymentClient)
        .refund(
            order.getPayments().get(0).getPaymentNo(),
            new BigDecimal("99.00"),
            "CNY",
            "refund:" + requested.getRefundNo() + ":payment");
    verify(inventoryClient)
        .restore(
            eq(order.getOrderNo()),
            anyList(),
            eq("refund:" + requested.getRefundNo() + ":inventory"));
  }

  @Test
  void retryRefundAfterInventoryFailureOnlyRetriesMissingStep() {
    Order order = paidOrder();
    givenOrder(order);
    Refund requested = service.requestRefund(order.getOrderNo(), "refund", "key-1");
    givenOrderByRefund(order, requested);
    when(paymentClient.refund(any(), any(), any(), any()))
        .thenReturn(new PaymentClient.ExternalRefund("EXT-REF"));
    doThrow(new IllegalStateException("inventory failed"))
        .doNothing()
        .when(inventoryClient)
        .restore(any(), anyList(), any());

    assertThatThrownBy(
            () -> service.approveRefund(requested.getRefundNo(), "admin", "yes", "key-2"))
        .isInstanceOf(IllegalStateException.class);
    assertThat(requested.isPaymentRefunded()).isTrue();
    assertThat(requested.isInventoryRestored()).isFalse();
    assertThat(requested.getStatus()).isEqualTo(RefundStatus.FAILED);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUND_FAILED);

    Refund retried = service.retryRefund(requested.getRefundNo(), "key-3");

    assertThat(retried).isSameAs(requested);
    assertThat(retried.getStatus()).isEqualTo(RefundStatus.SUCCEEDED);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
    verify(paymentClient, times(1)).refund(any(), any(), any(), any());
    verify(inventoryClient, times(2)).restore(any(), anyList(), any());
  }

  @Test
  void externalRefundFailureMarksRefundAndOrderFailed() {
    Order order = paidOrder();
    givenOrder(order);
    Refund requested = service.requestRefund(order.getOrderNo(), "refund", "key-1");
    givenOrderByRefund(order, requested);
    when(paymentClient.refund(any(), any(), any(), any()))
        .thenThrow(new IllegalStateException("payment failed"));

    assertThatThrownBy(
            () -> service.approveRefund(requested.getRefundNo(), "admin", "yes", "key-2"))
        .isInstanceOf(IllegalStateException.class);

    assertThat(requested.getStatus()).isEqualTo(RefundStatus.FAILED);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUND_FAILED);
    // One save from requestRefund, one from the failure-compensation path in executeRefund.
    verify(orderRepository, times(2)).saveAndFlush(order);
  }

  @Test
  void createShipmentValidatesAgainstRealOrderAndAllocatesShipment() {
    Order order = paidOrder();
    givenOrder(order);
    when(logisticsClient.createShipment(
            eq(order.getOrderNo()),
            any(),
            eq("MOCK_EXPRESS"),
            eq(
                new AddressSnapshot(
                    "Ada", "13800000000", "Shanghai", "Shanghai", "Pudong", "No. 1 Road")),
            anyList(),
            eq("key")))
        .thenReturn(new LogisticsClient.LogisticsShipment("TRACK-1"));

    Shipment shipment =
        service.createShipment(
            order.getOrderNo(), "MOCK_EXPRESS", List.of(new ShipmentLine("item-1", 1)), "key");

    assertThat(shipment.getTrackingNo()).isEqualTo("TRACK-1");
    assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.SHIPPED);
    assertThat(order.getShipments()).containsExactly(shipment);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    verify(orderRepository).saveAndFlush(order);
  }

  @Test
  void confirmReceiptDeliversShipmentAndDuplicateNotificationSkipsLogistics() {
    Order order = paidOrder();
    givenOrder(order);
    when(logisticsClient.createShipment(
            eq(order.getOrderNo()), any(), eq("MOCK_EXPRESS"), any(), anyList(), eq("ship-key")))
        .thenReturn(new LogisticsClient.LogisticsShipment("TRACK-1"));
    service.createShipment(
        order.getOrderNo(), "MOCK_EXPRESS", List.of(new ShipmentLine("item-1", 1)), "ship-key");
    Shipment shipment = order.getShipments().get(0);

    Order result =
        service.confirmReceipt(order.getOrderNo(), shipment.getShipmentNo(), "receipt-key");

    assertThat(result.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
    verify(logisticsClient)
        .confirmReceipt("TRACK-1", "confirm-receipt:" + shipment.getShipmentNo());

    service.confirmReceipt(order.getOrderNo(), shipment.getShipmentNo(), "receipt-key-2");

    verify(logisticsClient, times(1)).confirmReceipt(any(), any());
  }

  @Test
  void missingOrderFailsExplicitly() {
    when(orderRepository.findByOrderNoForUpdate("missing")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.createPayment("missing", "key"))
        .isInstanceOf(OrderNotFoundException.class);
  }

  private void givenOrder(Order order) {
    when(orderRepository.findByOrderNoForUpdate(order.getOrderNo())).thenReturn(Optional.of(order));
  }

  private void givenOrderByPayment(Order order, Payment payment) {
    when(orderRepository.findByPaymentNoForUpdate(payment.getPaymentNo()))
        .thenReturn(Optional.of(order));
  }

  private void givenOrderByRefund(Order order, Refund refund) {
    when(orderRepository.findByRefundNoForUpdate(refund.getRefundNo()))
        .thenReturn(Optional.of(order));
  }

  private static OrderItem item(int quantity) {
    OrderItem item = new OrderItem();
    item.setId("item-1");
    item.setSkuId("SKU-001");
    item.setProductName("Mouse");
    item.setUnitPrice(new BigDecimal("99.00"));
    item.setQuantity(quantity);
    return item;
  }

  private static Order order() {
    Order order =
        Order.create(
            "customer-1",
            "CNY",
            "Ada",
            "13800000000",
            "Shanghai",
            "Shanghai",
            "Pudong",
            "No. 1 Road",
            List.of(item(1)));
    order.setInventoryReservationId("reservation");
    return order;
  }

  private static Order paidOrder() {
    Order order = order();
    order.markPaid(order.addPayment("token"), "external-payment");
    return order;
  }
}
