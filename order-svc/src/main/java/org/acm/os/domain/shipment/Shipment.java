package org.acm.os.domain.shipment;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.acm.os.domain.order.OrderStateConflictException;
import org.acm.os.domain.shared.AuditMetadata;
import org.acm.os.domain.shared.BusinessNumberGenerator;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

@Entity
@Table(name = "shipments")
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public final class Shipment extends AuditMetadata {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String shipmentNo;

  @Enumerated(EnumType.STRING)
  private ShipmentStatus status;

  private String carrierCode;
  private String trackingNo;
  private LocalDateTime shippedAt;
  private LocalDateTime deliveredAt;

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "shipment_id", nullable = false)
  @Fetch(FetchMode.SUBSELECT)
  private List<ShipmentItem> items = new ArrayList<>();

  public static Shipment create(
      String shipmentNo, String carrierCode, String trackingNo, List<ShipmentItem> items) {
    if (items == null || items.isEmpty()) {
      throw new IllegalArgumentException("Shipment must contain at least one item");
    }
    Shipment shipment = new Shipment();
    shipment.shipmentNo = shipmentNo;
    shipment.status = ShipmentStatus.SHIPPED;
    shipment.carrierCode = carrierCode;
    shipment.trackingNo = trackingNo;
    shipment.shippedAt = LocalDateTime.now();
    shipment.items.addAll(items);
    return shipment;
  }

  public List<ShipmentItem> getItems() {
    return Collections.unmodifiableList(items);
  }

  public void deliver() {
    if (status == ShipmentStatus.DELIVERED) {
      return;
    }
    if (status != ShipmentStatus.SHIPPED) {
      throw new OrderStateConflictException("Only a shipped package can be delivered");
    }
    status = ShipmentStatus.DELIVERED;
    deliveredAt = LocalDateTime.now();
  }
}
