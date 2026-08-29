package org.acm.os.domain.shipment;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "shipment_items")
@EqualsAndHashCode
@Getter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public final class ShipmentItem {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long orderItemId;
  private Integer quantity;

  public static ShipmentItem of(Long orderItemId, Integer quantity) {
    Objects.requireNonNull(orderItemId, "orderItemId");
    if (quantity == null || quantity < 1) {
      throw new IllegalArgumentException("Shipment quantity must be positive");
    }
    ShipmentItem item = new ShipmentItem();
    item.orderItemId = orderItemId;
    item.quantity = quantity;
    return item;
  }
}
