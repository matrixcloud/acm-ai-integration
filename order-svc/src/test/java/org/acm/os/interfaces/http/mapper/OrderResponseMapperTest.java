package org.acm.os.interfaces.http.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.acm.os.domain.order.Order;
import org.acm.os.domain.order.OrderItem;
import org.acm.os.interfaces.http.response.CreateOrderResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class OrderResponseMapperTest {

  private final OrderResponseMapper mapper = Mappers.getMapper(OrderResponseMapper.class);

  @Test
  void mapsOrderAndItemsToResponse() {
    OrderItem item = new OrderItem();
    item.setSkuId("SKU-001");
    item.setProductName("Mouse");
    item.setUnitPrice(new BigDecimal("99.00"));
    item.setQuantity(2);
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
            List.of(item));
    LocalDateTime createdAt = LocalDateTime.of(2026, 8, 29, 12, 0);
    order.setCreatedAt(createdAt);

    CreateOrderResponse response = mapper.toResponse(order);

    assertThat(response.getOrderNo()).isEqualTo(order.getOrderNo());
    assertThat(response.getCustomerId()).isEqualTo("customer-1");
    assertThat(response.getStatus()).isEqualTo("PENDING_PAYMENT");
    assertThat(response.getCurrency()).isEqualTo("CNY");
    assertThat(response.getItemTotal()).isEqualByComparingTo("198.00");
    assertThat(response.getCreatedAt()).isEqualTo(createdAt);
    assertThat(response.getItems())
        .singleElement()
        .satisfies(
            mapped -> {
              assertThat(mapped.getLineNo()).isEqualTo(1);
              assertThat(mapped.getSkuId()).isEqualTo("SKU-001");
              assertThat(mapped.getLineAmount()).isEqualByComparingTo("198.00");
            });
    assertThat(mapper.toResponseList(List.of(order))).containsExactly(response);
  }
}
