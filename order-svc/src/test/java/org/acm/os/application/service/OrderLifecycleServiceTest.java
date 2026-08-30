package org.acm.os.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
import org.acm.os.application.port.out.PaymentClient;
import org.acm.os.domain.order.Order;
import org.acm.os.domain.order.OrderItem;
import org.acm.os.domain.order.OrderNotFoundException;
import org.acm.os.domain.order.OrderRepository;
import org.acm.os.domain.order.OrderStatus;
import org.acm.os.domain.payment.Payment;
import org.acm.os.domain.payment.PaymentStatus;
import org.acm.os.domain.refund.Refund;
import org.acm.os.domain.shipment.Shipment;
import org.acm.os.domain.shipment.ShipmentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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
    when(idempotencyService.execute(any(), any()))
        .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
    when(idempotencyService.executeRetryable(any(), any()))
        .thenAnswer(
            invocation -> {
              try {
                return ((Supplier<?>) invocation.getArgument(1)).get();
              } catch (PersistedRetryableFailureException exception) {
                throw new RetryableOperationException(exception.original());
              }
            });
    when(orderRepository.saveAndFlush(any(Order.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void createsPaymentUsingServerAmount() {
    Order order = order();
    Payment payment = mock(Payment.class);
    when(orderRepository.findByOrderNoForUpdate("ORD-1")).thenReturn(Optional.of(order));
    when(order.getPayableTotal()).thenReturn(new BigDecimal("99.00"));
    when(order.getCurrency()).thenReturn("CNY");
    when(paymentClient.create("ORD-1", new BigDecimal("99.00"), "CNY", "key"))
        .thenReturn(new PaymentClient.PaymentSession("token"));
    when(order.addPayment("token")).thenReturn(payment);

    assertThat(service.createPayment("ORD-1", "key")).isSameAs(payment);
    verify(orderRepository).saveAndFlush(order);
  }

  @Test
  void succeedsFreshPaymentAfterInventoryConfirmation() {
    Order order = orderByPayment();
    Payment payment = mock(Payment.class);
    when(order.payment("PAY-1")).thenReturn(payment);
    when(payment.getStatus()).thenReturn(PaymentStatus.CREATED);
    when(order.getInventoryReservationId()).thenReturn("reservation");

    assertThat(service.succeedPayment("PAY-1", "EXT-1", "key")).isSameAs(order);
    verify(inventoryClient).confirm("reservation", "payment-confirm:EXT-1");
    verify(order).markPaid(payment, "EXT-1");
  }

  @Test
  void duplicateSuccessfulPaymentSkipsInventory() {
    Order order = orderByPayment();
    Payment payment = mock(Payment.class);
    when(order.payment("PAY-1")).thenReturn(payment);
    when(payment.getStatus()).thenReturn(PaymentStatus.SUCCEEDED);

    service.succeedPayment("PAY-1", "EXT-1", "key");

    verify(inventoryClient, never()).confirm(any(), any());
    verify(order).markPaid(payment, "EXT-1");
  }

  @Test
  void marksPaymentFailed() {
    Order order = orderByPayment();
    Payment payment = mock(Payment.class);
    when(order.payment("PAY-1")).thenReturn(payment);

    service.failPayment("PAY-1", "key");

    verify(order).markPaymentFailed(payment);
  }

  @Test
  void cancelsPendingOrderByReleasingReservation() {
    Order order = order();
    when(orderRepository.findByOrderNoForUpdate("ORD-1")).thenReturn(Optional.of(order));
    when(order.getStatus()).thenReturn(OrderStatus.PENDING_PAYMENT);
    when(order.getInventoryReservationId()).thenReturn("reservation");

    service.cancel("ORD-1", "reason", "key");

    verify(inventoryClient).release("reservation", "cancel-pending:ORD-1");
    verify(order).cancelPending();
  }

  @Test
  void cancelsPaidOrderWithRefundAndInventoryRestore() {
    Order order = paidOrderForRefund();
    Refund refund = refund(false, false);
    when(order.getStatus()).thenReturn(OrderStatus.PAID);
    when(order.startCancel(eq("reason"), any())).thenReturn(refund);

    service.cancel("ORD-1", "reason", "key");

    verify(refund).markPaymentRefunded("EXT-REF");
    verify(refund).markInventoryRestored();
    verify(order).completeRefund(refund);
  }

  @Test
  void requestsAndRejectsRefund() {
    Order order = order();
    Refund refund = mock(Refund.class);
    when(orderRepository.findByOrderNoForUpdate("ORD-1")).thenReturn(Optional.of(order));
    when(order.requestRefund("reason")).thenReturn(refund);
    assertThat(service.requestRefund("ORD-1", "reason", "key")).isSameAs(refund);

    when(orderRepository.findByRefundNoForUpdate("REF-1")).thenReturn(Optional.of(order));
    when(order.refund("REF-1")).thenReturn(refund);
    assertThat(service.rejectRefund("REF-1", "admin", "no", "key-2")).isSameAs(refund);
    verify(order).rejectRefund(refund, "admin", "no");
  }

  @Test
  void approvesRefundAndExecutesBothExternalSteps() {
    Order order = paidOrderForRefund();
    Refund refund = refund(false, false);
    when(order.refund("REF-1")).thenReturn(refund);

    assertThat(service.approveRefund("REF-1", "admin", "yes", "key")).isSameAs(refund);

    verify(order).approveRefund(refund, "admin", "yes");
    verify(paymentClient).refund("PAY-1", new BigDecimal("99.00"), "CNY", "refund:REF-1:payment");
    verify(inventoryClient).restore(eq("ORD-1"), anyList(), eq("refund:REF-1:inventory"));
  }

  @Test
  void retrySkipsAlreadyCompletedPaymentRefund() {
    Order order = paidOrderForRefund();
    Refund refund = refund(true, false);
    when(order.refund("REF-1")).thenReturn(refund);

    service.retryRefund("REF-1", "key");

    verify(order).retryRefund(refund);
    verify(paymentClient, never()).refund(any(), any(), any(), any());
    verify(inventoryClient).restore(eq("ORD-1"), anyList(), eq("refund:REF-1:inventory"));
  }

  @Test
  void externalRefundFailureMarksDomainFailure() {
    Order order = paidOrderForRefund();
    Refund refund = refund(false, false);
    when(order.refund("REF-1")).thenReturn(refund);
    when(paymentClient.refund(any(), any(), any(), any()))
        .thenThrow(new IllegalStateException("payment failed"));

    assertThatThrownBy(() -> service.approveRefund("REF-1", "admin", "yes", "key"))
        .isInstanceOf(IllegalStateException.class);
    verify(order).failRefund(refund);
    verify(orderRepository).saveAndFlush(order);
  }

  @Test
  void createsShipmentAfterDomainValidationAndExternalCall() {
    Order order = order();
    when(orderRepository.findByOrderNoForUpdate("ORD-1")).thenReturn(Optional.of(order));
    when(order.getRecipientName()).thenReturn("Ada");
    when(order.getRecipientPhone()).thenReturn("13800000000");
    when(order.getProvince()).thenReturn("Shanghai");
    when(order.getCity()).thenReturn("Shanghai");
    when(order.getDistrict()).thenReturn("Pudong");
    when(order.getDetailAddress()).thenReturn("Road 1");
    when(logisticsClient.createShipment(
            eq("ORD-1"), any(), eq("MOCK_EXPRESS"), any(), anyList(), eq("key")))
        .thenReturn(new LogisticsClient.LogisticsShipment("TRACK-1"));

    Shipment result =
        service.createShipment("ORD-1", "MOCK_EXPRESS", List.of(new ShipmentLine(1L, 1)), "key");

    assertThat(result.getTrackingNo()).isEqualTo("TRACK-1");
    verify(order).allocateShipment(result);
  }

  @Test
  void confirmsReceiptAndSkipsDuplicateLogisticsCall() {
    Order order = order();
    Shipment shipment = mock(Shipment.class);
    when(orderRepository.findByOrderNoForUpdate("ORD-1")).thenReturn(Optional.of(order));
    when(order.shipment("SHP-1")).thenReturn(shipment);
    when(shipment.getStatus()).thenReturn(ShipmentStatus.SHIPPED);
    when(shipment.getTrackingNo()).thenReturn("TRACK-1");
    when(shipment.getShipmentNo()).thenReturn("SHP-1");

    service.confirmReceipt("ORD-1", "SHP-1", "key");
    verify(logisticsClient).confirmReceipt("TRACK-1", "confirm-receipt:SHP-1");

    when(shipment.getStatus()).thenReturn(ShipmentStatus.DELIVERED);
    service.confirmReceipt("ORD-1", "SHP-1", "key-2");
    verify(logisticsClient, times(1)).confirmReceipt("TRACK-1", "confirm-receipt:SHP-1");
  }

  @Test
  void missingOrderFailsExplicitly() {
    when(orderRepository.findByOrderNoForUpdate("missing")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.createPayment("missing", "key"))
        .isInstanceOf(OrderNotFoundException.class);
  }

  private Order order() {
    Order order = mock(Order.class);
    when(order.getOrderNo()).thenReturn("ORD-1");
    return order;
  }

  private Order orderByPayment() {
    Order order = order();
    when(orderRepository.findByPaymentNoForUpdate("PAY-1")).thenReturn(Optional.of(order));
    return order;
  }

  private Order paidOrderForRefund() {
    Order order = order();
    Payment payment = mock(Payment.class);
    OrderItem item = mock(OrderItem.class);
    when(orderRepository.findByOrderNoForUpdate("ORD-1")).thenReturn(Optional.of(order));
    when(orderRepository.findByRefundNoForUpdate("REF-1")).thenReturn(Optional.of(order));
    when(order.getPayments()).thenReturn(List.of(payment));
    when(payment.getStatus()).thenReturn(PaymentStatus.SUCCEEDED);
    when(payment.getPaymentNo()).thenReturn("PAY-1");
    when(order.getItems()).thenReturn(List.of(item));
    when(item.getSkuId()).thenReturn("SKU-1");
    when(item.getQuantity()).thenReturn(1);
    return order;
  }

  private Refund refund(boolean paymentRefunded, boolean inventoryRestored) {
    Refund refund = mock(Refund.class);
    when(refund.getRefundNo()).thenReturn("REF-1");
    when(refund.isPaymentRefunded()).thenReturn(paymentRefunded);
    when(refund.isInventoryRestored()).thenReturn(inventoryRestored);
    when(refund.getAmount()).thenReturn(new BigDecimal("99.00"));
    when(refund.getCurrency()).thenReturn("CNY");
    when(paymentClient.refund(any(), any(), any(), any()))
        .thenReturn(new PaymentClient.ExternalRefund("EXT-REF"));
    return refund;
  }
}
