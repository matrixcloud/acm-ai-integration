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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.acm.os.domain.shared.AuditMetadata;

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

  private static final DateTimeFormatter TIMESTAMP_FORMAT =
          DateTimeFormatter.ofPattern("yyMMddHHmmss");

  /** @return an order number with the {@code ORD} prefix */
  private static String generateOrderNo() {
    return generate("ORD");
  }

  private static String generate(String prefix) {
    String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
    int random = ThreadLocalRandom.current().nextInt(0, 1_000_000);
    return prefix + timestamp + String.format("%06d", random);
  }
}
