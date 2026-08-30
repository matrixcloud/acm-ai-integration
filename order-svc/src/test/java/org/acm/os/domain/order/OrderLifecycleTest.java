package org.acm.os.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.acm.os.domain.payment.Payment;
import org.acm.os.domain.payment.PaymentStatus;
import org.acm.os.domain.refund.Refund;
import org.acm.os.domain.refund.RefundStatus;
import org.acm.os.domain.shipment.Shipment;
import org.acm.os.domain.shipment.ShipmentItem;
import org.acm.os.domain.shipment.ShipmentStatus;
import org.junit.jupiter.api.Test;

class OrderLifecycleTest {

  @Test
  void paymentCanSucceedAndDuplicateNotificationIsIdempotent() {
    Order order = order(2);
    Payment payment = order.addPayment("token");

    order.claimPaymentNotification(payment, "external-1");
    assertThatThrownBy(() -> order.claimPaymentNotification(payment, "external-2"))
        .isInstanceOf(IllegalStateException.class);
    order.markPaid(payment, "external-1");
    order.markPaid(payment, "external-1");

    assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    assertThat(payment.getPaidAt()).isNotNull();
    assertThatThrownBy(() -> order.markPaid(payment, "external-2"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> order.addPayment("second"))
        .isInstanceOf(OrderStateConflictException.class);
  }

  @Test
  void activePaymentAndFailedPaymentRulesAreExplicit() {
    Order order = order(1);
    Payment payment = order.addPayment("token");
    assertThatThrownBy(() -> order.addPayment("other"))
        .isInstanceOf(OrderStateConflictException.class);

    order.markPaymentFailed(payment);
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    payment.succeed("external-after-retry");
    assertThat(order.addPayment("retry")).isNotNull();
    assertThatThrownBy(() -> order.payment("missing"))
        .isInstanceOf(OrderStateConflictException.class);
  }

  @Test
  void pendingOrderCanCancelWithoutRefund() {
    Order order = order(1);
    order.cancelPending();
    assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
    assertThat(order.getRefunds()).isEmpty();
    assertThatThrownBy(order::cancelPending).isInstanceOf(OrderStateConflictException.class);
  }

  @Test
  void reviewedRefundCanBeRejectedOrCompleted() {
    Order rejectedOrder = paidOrder(1);
    Refund rejected = rejectedOrder.requestRefund("changed mind");
    assertThat(rejectedOrder.getStatus()).isEqualTo(OrderStatus.REFUND_REVIEW);
    rejectedOrder.rejectRefund(rejected, "admin", "no");
    assertThat(rejected.getStatus()).isEqualTo(RefundStatus.REJECTED);
    assertThat(rejectedOrder.getStatus()).isEqualTo(OrderStatus.PAID);

    Order approvedOrder = paidOrder(1);
    Refund approved = approvedOrder.requestRefund("changed mind");
    approvedOrder.approveRefund(approved, "admin", "yes");
    approved.markPaymentRefunded("external-refund");
    approved.markInventoryRestored();
    approvedOrder.completeRefund(approved);
    assertThat(approvedOrder.getStatus()).isEqualTo(OrderStatus.REFUNDED);
    assertThat(approved.getStatus()).isEqualTo(RefundStatus.SUCCEEDED);
    assertThat(approved.getRefundedAt()).isNotNull();
  }

  @Test
  void failedAutoCancelCanRetryOnlyMissingSteps() {
    Order order = paidOrder(1);
    Refund refund = order.startCancel("cancel", "REF-CANCEL-1");
    refund.markPaymentRefunded("external-refund");
    order.failRefund(refund);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCEL_FAILED);

    order.retryRefund(refund);
    refund.markInventoryRestored();
    order.completeRefund(refund);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
    assertThat(refund.isPaymentRefunded()).isTrue();
    assertThat(refund.isInventoryRestored()).isTrue();
  }

  @Test
  void splitShipmentTransitionsToCompletedAndBlocksRefund() {
    Order order = paidOrder(2);
    Shipment first =
        Shipment.create("SHP-1", "MOCK_EXPRESS", "TRACK-1", List.of(ShipmentItem.of(1L, 1)));
    order.allocateShipment(first);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIALLY_SHIPPED);
    assertThatThrownBy(() -> order.requestRefund("refund"))
        .isInstanceOf(OrderNotRefundableException.class);
    order.confirmShipmentDelivered("SHP-1");
    assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIALLY_SHIPPED);

    Shipment second =
        Shipment.create("SHP-2", "MOCK_EXPRESS", "TRACK-2", List.of(ShipmentItem.of(1L, 1)));
    order.allocateShipment(second);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);

    order.confirmShipmentDelivered("SHP-2");
    order.confirmShipmentDelivered("SHP-2");
    assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    assertThat(first.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
  }

  @Test
  void shipmentRejectsUnknownAndExcessQuantitiesAndInvalidStates() {
    Order order = paidOrder(1);
    assertThatThrownBy(() -> order.validateShipmentItems(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                order.validateShipmentItems(
                    List.of(ShipmentItem.of(1L, 1), ShipmentItem.of(1L, 1))))
        .isInstanceOf(ShipmentQuantityExceededException.class);
    assertThatThrownBy(() -> order.validateShipmentItems(List.of(ShipmentItem.of(99L, 1))))
        .isInstanceOf(ShipmentQuantityExceededException.class);
    assertThatThrownBy(() -> order.validateShipmentItems(List.of(ShipmentItem.of(1L, 2))))
        .isInstanceOf(ShipmentQuantityExceededException.class);
    assertThatThrownBy(() -> order.confirmShipmentDelivered("missing"))
        .isInstanceOf(OrderStateConflictException.class);

    Order pending = order(1);
    assertThatThrownBy(() -> pending.validateShipmentItems(List.of(ShipmentItem.of(1L, 1))))
        .isInstanceOf(OrderStateConflictException.class);
  }

  private static Order paidOrder(int quantity) {
    Order order = order(quantity);
    Payment payment = order.addPayment("token");
    order.markPaid(payment, "external-payment");
    return order;
  }

  private static Order order(int quantity) {
    OrderItem item = new OrderItem();
    item.setId(1L);
    item.setSkuId("SKU-001");
    item.setProductName("Mouse");
    item.setUnitPrice(new BigDecimal("99.00"));
    item.setQuantity(quantity);
    return Order.create(
        "customer-1",
        "CNY",
        "Ada",
        "13800000000",
        "Shanghai",
        "Shanghai",
        "Pudong",
        "No. 1 Road",
        List.of(item));
  }
}
