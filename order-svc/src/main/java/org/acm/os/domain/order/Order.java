package org.acm.os.domain.order;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.acm.os.domain.shared.AuditMetadata;
import org.acm.os.domain.shared.BusinessNumberGenerator;
import org.acm.os.domain.payment.Payment;
import org.acm.os.domain.payment.PaymentStatus;
import org.acm.os.domain.refund.Refund;
import org.acm.os.domain.refund.RefundStatus;
import org.acm.os.domain.refund.RefundType;
import org.acm.os.domain.shipment.Shipment;
import org.acm.os.domain.shipment.ShipmentItem;
import org.acm.os.domain.shipment.ShipmentStatus;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

/**
 * The Order aggregate root.
 *
 * <p>Encapsulates the business invariants for creating an order: items must be non-empty, duplicate
 * SKUs are rejected, and {@code itemTotal}/{@code payableTotal} are derived from the items — never
 * set externally.
 */
@Entity
@Table(name = "orders")
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public final class Order extends AuditMetadata {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String orderNo;
  private String customerId;
  @Enumerated(EnumType.STRING)
  private OrderStatus status;
  private String currency;

  private BigDecimal itemTotal;
  private BigDecimal payableTotal;
  @Setter
  private String inventoryReservationId;

  // Shipping recipient, denormalized into the order for query convenience.
  private String recipientName;
  private String recipientPhone;
  private String province;
  private String city;
  private String district;
  private String detailAddress;

  @Version private Long version;

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "order_id", nullable = false)
  private List<OrderItem> items = new ArrayList<>();

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "order_id", nullable = false)
  @Fetch(FetchMode.SUBSELECT)
  private List<Payment> payments = new ArrayList<>();

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "order_id", nullable = false)
  @Fetch(FetchMode.SUBSELECT)
  private List<Refund> refunds = new ArrayList<>();

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "order_id", nullable = false)
  @Fetch(FetchMode.SUBSELECT)
  private List<Shipment> shipments = new ArrayList<>();

  /**
   * Factory for a new order.
   *
   * @param customerId customer placing the order
   * @param currency ISO-4217 currency code
   * @param recipientName recipient full name
   * @param recipientPhone recipient phone number
   * @param province shipping address: province
   * @param city shipping address: city
   * @param district shipping address: district
   * @param detailAddress shipping address: detailed street address
   * @param items initial order lines (must be non-empty)
   * @return a new Order in {@link OrderStatus#PENDING_PAYMENT} with derived totals
   */
  public static Order create(
      String customerId,
      String currency,
      String recipientName,
      String recipientPhone,
      String province,
      String city,
      String district,
      String detailAddress,
      List<OrderItem> items) {
    if (items == null || items.isEmpty()) {
      throw new IllegalArgumentException("Order must contain at least one item");
    }
    Order order = new Order();
    order.orderNo = generateOrderNo();
    order.customerId = customerId;
    order.currency = currency;
    order.status = OrderStatus.PENDING_PAYMENT;
    order.recipientName = recipientName;
    order.recipientPhone = recipientPhone;
    order.province = province;
    order.city = city;
    order.district = district;
    order.detailAddress = detailAddress;
    order.replaceItems(items);
    return order;
  }

  /**
   * Replaces all order lines with {@code newItems}, re-numbering from 1 and recomputing totals.
   *
   * <p>Exposes {@link List} (unmodifiable) for persistence/mapping; callers must use this method to
   * mutate items.
   */
  public void replaceItems(List<OrderItem> newItems) {
    if (newItems == null || newItems.isEmpty()) {
      throw new IllegalArgumentException("Order must contain at least one item");
    }
    verifyNoDuplicateSkus(newItems);
    List<OrderItem> copy = new ArrayList<>(newItems.size());
    for (int i = 0; i < newItems.size(); i++) {
      OrderItem item = newItems.get(i);
      BigDecimal lineAmount =
          item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
      item.setLineNo(i + 1);
      item.setLineAmount(lineAmount);
      copy.add(item);
    }
    this.items.clear();
    this.items.addAll(copy);
    recomputeTotals();
  }

  /** Recomputes {@code itemTotal} and {@code payableTotal} from current items. */
  private void recomputeTotals() {
    BigDecimal total =
        this.items.stream()
            .map(OrderItem::getLineAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    this.itemTotal = total;
    this.payableTotal = total; // no discounts/shipping yet
  }

  private static void verifyNoDuplicateSkus(List<OrderItem> items) {
    long distinctSkus = items.stream().map(OrderItem::getSkuId).distinct().count();
    if (distinctSkus != items.size()) {
      throw new DuplicateSkuException("Order contains duplicate SKU IDs");
    }
  }

  public List<OrderItem> getItems() {
    return Collections.unmodifiableList(items);
  }

  public Set<String> skuIds() {
    return items.stream().map(OrderItem::getSkuId).collect(Collectors.toUnmodifiableSet());
  }

  public List<Payment> getPayments() {
    return Collections.unmodifiableList(payments);
  }

  public List<Refund> getRefunds() {
    return Collections.unmodifiableList(refunds);
  }

  public List<Shipment> getShipments() {
    return Collections.unmodifiableList(shipments);
  }

  public Payment addPayment(String paymentToken) {
    assertCanCreatePayment();
    Payment payment = Payment.create(currency, payableTotal, paymentToken);
    payments.add(payment);
    return payment;
  }

  public void assertCanCreatePayment() {
    requireStatus(OrderStatus.PENDING_PAYMENT, "create a payment");
    boolean activePayment =
        payments.stream().anyMatch(payment -> payment.getStatus() == PaymentStatus.CREATED);
    if (activePayment) {
      throw new OrderStateConflictException("Order already has an active payment");
    }
  }

  public void claimPaymentNotification(Payment payment, String externalPaymentNo) {
    requireStatus(OrderStatus.PENDING_PAYMENT, "claim payment notification");
    if (!payments.contains(payment)) {
      throw new IllegalArgumentException("Payment does not belong to this order");
    }
    payment.claimExternalPaymentNo(externalPaymentNo);
  }

  public void markPaid(Payment payment, String externalPaymentNo) {
    if (status == OrderStatus.PAID && payment.getStatus() == PaymentStatus.SUCCEEDED) {
      payment.succeed(externalPaymentNo);
      return;
    }
    requireStatus(OrderStatus.PENDING_PAYMENT, "complete payment");
    if (!payments.contains(payment)) {
      throw new IllegalArgumentException("Payment does not belong to this order");
    }
    if (!currency.equals(payment.getCurrency())
        || payableTotal.compareTo(payment.getAmount()) != 0) {
      throw new OrderStateConflictException("Payment amount does not match order payable total");
    }
    payment.succeed(externalPaymentNo);
    status = OrderStatus.PAID;
  }

  public void markPaymentFailed(Payment payment) {
    requireStatus(OrderStatus.PENDING_PAYMENT, "fail payment");
    if (!payments.contains(payment)) {
      throw new IllegalArgumentException("Payment does not belong to this order");
    }
    payment.fail();
  }

  public void cancelPending() {
    requireStatus(OrderStatus.PENDING_PAYMENT, "cancel order");
    status = OrderStatus.CANCELED;
  }

  public Refund startCancel(String reason, String refundNo) {
    assertRefundable();
    requireStatus(OrderStatus.PAID, "cancel order");
    Refund refund = Refund.autoCancel(refundNo, reason, currency, successfulPaymentAmount());
    refunds.add(refund);
    status = OrderStatus.CANCELING;
    return refund;
  }

  public Refund requestRefund(String reason) {
    assertRefundable();
    requireStatus(OrderStatus.PAID, "request refund");
    boolean pending =
        refunds.stream()
            .anyMatch(
                refund ->
                    refund.getStatus() == RefundStatus.PENDING_REVIEW
                        || refund.getStatus() == RefundStatus.PROCESSING
                        || refund.getStatus() == RefundStatus.FAILED);
    if (pending) {
      throw new OrderStateConflictException("Order already has an unfinished refund");
    }
    Refund refund = Refund.reviewed(reason, currency, successfulPaymentAmount());
    refunds.add(refund);
    status = OrderStatus.REFUND_REVIEW;
    return refund;
  }

  public void approveRefund(Refund refund, String reviewer, String comment) {
    requireStatus(OrderStatus.REFUND_REVIEW, "approve refund");
    requireRefund(refund, RefundType.REVIEWED_REFUND);
    refund.approve(reviewer, comment);
    status = OrderStatus.REFUNDING;
  }

  public void rejectRefund(Refund refund, String reviewer, String comment) {
    requireStatus(OrderStatus.REFUND_REVIEW, "reject refund");
    requireRefund(refund, RefundType.REVIEWED_REFUND);
    refund.reject(reviewer, comment);
    status = OrderStatus.PAID;
  }

  public void retryRefund(Refund refund) {
    if (status != OrderStatus.REFUND_FAILED && status != OrderStatus.CANCEL_FAILED) {
      throw new OrderStateConflictException("Order does not have a failed refund to retry");
    }
    requireRefund(refund, refund.getType());
    refund.retry();
    status =
        refund.getType() == RefundType.AUTO_CANCEL
            ? OrderStatus.CANCELING
            : OrderStatus.REFUNDING;
  }

  public void completeRefund(Refund refund) {
    requireRefund(refund, refund.getType());
    refund.complete();
    status =
        refund.getType() == RefundType.AUTO_CANCEL
            ? OrderStatus.CANCELED
            : OrderStatus.REFUNDED;
  }

  public void failRefund(Refund refund) {
    requireRefund(refund, refund.getType());
    refund.fail();
    status =
        refund.getType() == RefundType.AUTO_CANCEL
            ? OrderStatus.CANCEL_FAILED
            : OrderStatus.REFUND_FAILED;
  }

  public void allocateShipment(Shipment shipment) {
    if (status != OrderStatus.PAID && status != OrderStatus.PARTIALLY_SHIPPED) {
      if (hasShippedItems() || status == OrderStatus.SHIPPED || status == OrderStatus.COMPLETED) {
        throw new OrderNotRefundableException("Order '%s' has already been shipped".formatted(orderNo));
      }
      throw new OrderStateConflictException(
          "Order status %s does not allow shipment".formatted(status));
    }
    validateShipmentItems(shipment.getItems());
    shipments.add(shipment);
    status = allItemsAllocated() ? OrderStatus.SHIPPED : OrderStatus.PARTIALLY_SHIPPED;
  }

  public void validateShipmentItems(List<ShipmentItem> shipmentItems) {
    if (status != OrderStatus.PAID && status != OrderStatus.PARTIALLY_SHIPPED) {
      if (hasShippedItems() || status == OrderStatus.SHIPPED || status == OrderStatus.COMPLETED) {
        throw new OrderNotRefundableException("Order '%s' has already been shipped".formatted(orderNo));
      }
      throw new OrderStateConflictException(
          "Order status %s does not allow shipment".formatted(status));
    }
    if (shipmentItems == null || shipmentItems.isEmpty()) {
      throw new IllegalArgumentException("Shipment must contain at least one item");
    }
    long distinctOrderItems =
        shipmentItems.stream().map(ShipmentItem::getOrderItemId).distinct().count();
    if (distinctOrderItems != shipmentItems.size()) {
      throw new ShipmentQuantityExceededException(
          "Shipment contains duplicate order item IDs");
    }
    for (ShipmentItem shipmentItem : shipmentItems) {
      OrderItem orderItem =
          items.stream()
              .filter(item -> item.getId().equals(shipmentItem.getOrderItemId()))
              .findFirst()
              .orElseThrow(
                  () ->
                      new ShipmentQuantityExceededException(
                          "Order item '%s' does not belong to order"
                              .formatted(shipmentItem.getOrderItemId())));
      int alreadyShipped = shippedQuantity(orderItem.getId());
      if (alreadyShipped + shipmentItem.getQuantity() > orderItem.getQuantity()) {
        throw new ShipmentQuantityExceededException(
            "Shipment quantity exceeds remaining quantity for order item '%s'"
                .formatted(orderItem.getId()));
      }
    }
  }

  public void confirmShipmentDelivered(String shipmentNo) {
    if (status != OrderStatus.PARTIALLY_SHIPPED
        && status != OrderStatus.SHIPPED
        && status != OrderStatus.COMPLETED) {
      throw new OrderStateConflictException(
          "Order status %s does not allow receipt confirmation".formatted(status));
    }
    Shipment shipment =
        shipments.stream()
            .filter(value -> value.getShipmentNo().equals(shipmentNo))
            .findFirst()
            .orElseThrow(
                () -> new OrderStateConflictException("Shipment '%s' does not exist".formatted(shipmentNo)));
    shipment.deliver();
    if (status == OrderStatus.SHIPPED
        && shipments.stream().allMatch(value -> value.getStatus() == ShipmentStatus.DELIVERED)) {
      status = OrderStatus.COMPLETED;
    }
  }

  public Payment payment(String paymentNo) {
    return payments.stream()
        .filter(payment -> payment.getPaymentNo().equals(paymentNo))
        .findFirst()
        .orElseThrow(() -> new OrderStateConflictException("Payment '%s' does not exist".formatted(paymentNo)));
  }

  public Refund refund(String refundNo) {
    return refunds.stream()
        .filter(refund -> refund.getRefundNo().equals(refundNo))
        .findFirst()
        .orElseThrow(() -> new OrderStateConflictException("Refund '%s' does not exist".formatted(refundNo)));
  }

  public Shipment shipment(String shipmentNo) {
    return shipments.stream()
        .filter(shipment -> shipment.getShipmentNo().equals(shipmentNo))
        .findFirst()
        .orElseThrow(() -> new OrderStateConflictException("Shipment '%s' does not exist".formatted(shipmentNo)));
  }

  private void assertRefundable() {
    if (hasShippedItems()
        || status == OrderStatus.PARTIALLY_SHIPPED
        || status == OrderStatus.SHIPPED
        || status == OrderStatus.COMPLETED) {
      throw new OrderNotRefundableException("Order '%s' has already been shipped".formatted(orderNo));
    }
  }

  private boolean hasShippedItems() {
    return !shipments.isEmpty();
  }

  private BigDecimal successfulPaymentAmount() {
    return payments.stream()
        .filter(payment -> payment.getStatus() == PaymentStatus.SUCCEEDED)
        .findFirst()
        .map(Payment::getAmount)
        .orElseThrow(() -> new OrderStateConflictException("Order has no successful payment"));
  }

  private int shippedQuantity(Long orderItemId) {
    return shipments.stream()
        .flatMap(shipment -> shipment.getItems().stream())
        .filter(item -> item.getOrderItemId().equals(orderItemId))
        .mapToInt(ShipmentItem::getQuantity)
        .sum();
  }

  private boolean allItemsAllocated() {
    return items.stream()
        .allMatch(item -> shippedQuantity(item.getId()) == item.getQuantity());
  }

  private void requireRefund(Refund refund, RefundType expectedType) {
    if (!refunds.contains(refund) || refund.getType() != expectedType) {
      throw new IllegalArgumentException("Refund does not belong to this order");
    }
  }

  private void requireStatus(OrderStatus expected, String operation) {
    if (status != expected) {
      throw new OrderStateConflictException(
          "Order status %s does not allow %s".formatted(status, operation));
    }
  }

  /** @return an order number with the {@code ORD} prefix */
  private static String generateOrderNo() {
    return BusinessNumberGenerator.generate("ORD");
  }
}
