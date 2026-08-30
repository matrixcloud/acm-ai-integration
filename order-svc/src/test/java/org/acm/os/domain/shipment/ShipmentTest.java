package org.acm.os.domain.shipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ShipmentTest {

  @Test
  void validatesItemsAndDeliveryIsIdempotent() {
    assertThatThrownBy(() -> ShipmentItem.of(null, 1)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> ShipmentItem.of(1L, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ShipmentItem.of(1L, 0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Shipment.create("S", "C", "T", List.of()))
        .isInstanceOf(IllegalArgumentException.class);

    Shipment shipment = Shipment.create("S", "MOCK_EXPRESS", "T", List.of(ShipmentItem.of(1L, 1)));
    shipment.deliver();
    shipment.deliver();
    assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
    assertThat(shipment.getDeliveredAt()).isNotNull();
    assertThatThrownBy(() -> shipment.getItems().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
