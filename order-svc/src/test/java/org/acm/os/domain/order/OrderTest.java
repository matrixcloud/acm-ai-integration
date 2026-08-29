package org.acm.os.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderTest {

  @Test
  void createDerivesOrderStateLinesAndTotals() {
    OrderItem first = item("SKU-001", "10.00", 2);
    OrderItem second = item("SKU-002", "0.50", 3);

    Order order = createOrder(List.of(first, second));

    assertThat(order.getOrderNo()).startsWith("ORD").hasSize(21);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    assertThat(order.getItemTotal()).isEqualByComparingTo("21.50");
    assertThat(order.getPayableTotal()).isEqualByComparingTo("21.50");
    assertThat(order.getItems())
        .extracting(OrderItem::getLineNo, OrderItem::getLineAmount)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(1, new BigDecimal("20.00")),
            org.assertj.core.groups.Tuple.tuple(2, new BigDecimal("1.50")));
    assertThat(order.skuIds()).containsExactlyInAnyOrder("SKU-001", "SKU-002");
  }

  @Test
  void createRejectsNullOrEmptyItems() {
    assertThatThrownBy(() -> createOrder(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Order must contain at least one item");
    assertThatThrownBy(() -> createOrder(List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Order must contain at least one item");
  }

  @Test
  void createRejectsDuplicateSkus() {
    List<OrderItem> items = List.of(item("SKU-001", "10.00", 1), item("SKU-001", "20.00", 1));

    assertThatThrownBy(() -> createOrder(items))
        .isInstanceOf(DuplicateSkuException.class)
        .hasMessage("Order contains duplicate SKU IDs");
  }

  @Test
  void replaceItemsRecalculatesDerivedValuesAndProtectsCollection() {
    Order order = createOrder(List.of(item("SKU-001", "10.00", 1)));
    OrderItem replacement = item("SKU-003", "2.50", 4);

    order.replaceItems(List.of(replacement));

    assertThat(order.getItems()).containsExactly(replacement);
    assertThat(replacement.getLineNo()).isEqualTo(1);
    assertThat(replacement.getLineAmount()).isEqualByComparingTo("10.00");
    assertThat(order.getItemTotal()).isEqualByComparingTo("10.00");
    assertThatThrownBy(() -> order.getItems().add(item("SKU-004", "1.00", 1)))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void replaceItemsRejectsNullOrEmptyItems() {
    Order order = createOrder(List.of(item("SKU-001", "10.00", 1)));

    assertThatThrownBy(() -> order.replaceItems(null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> order.replaceItems(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static Order createOrder(List<OrderItem> items) {
    return Order.create(
        "customer-1",
        "CNY",
        "Ada",
        "13800000000",
        "Shanghai",
        "Shanghai",
        "Pudong",
        "No. 1 Road",
        items);
  }

  private static OrderItem item(String skuId, String unitPrice, int quantity) {
    OrderItem item = new OrderItem();
    item.setSkuId(skuId);
    item.setProductName("Product " + skuId);
    item.setUnitPrice(new BigDecimal(unitPrice));
    item.setQuantity(quantity);
    return item;
  }
}
