package org.acm.os.infra.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.acm.os.application.port.out.ExternalDependencyException;
import org.acm.os.application.port.out.LogisticsClient.AddressSnapshot;
import org.acm.os.application.port.out.LogisticsClient.LogisticsShipment;
import org.acm.os.application.port.out.LogisticsClient.ShipmentItem;
import org.junit.jupiter.api.Test;

class LogisticsClientImplTest {
  private final LogisticsClientImpl client = new LogisticsClientImpl(new MockFailureRegistry());
  private final AddressSnapshot address =
      new AddressSnapshot("Ada", "13800000000", "Shanghai", "Shanghai", "Pudong", "Road 1");

  @Test
  void createAndConfirmAreIdempotentForSameRequest() {
    List<ShipmentItem> items = List.of(new ShipmentItem(1L, 1));
    LogisticsShipment first =
        client.createShipment("ORD-1", "SHP-1", "MOCK_EXPRESS", address, items, "key");
    LogisticsShipment replay =
        client.createShipment("ORD-1", "SHP-1", "MOCK_EXPRESS", address, items, "key");

    assertThat(replay).isEqualTo(first);
    client.confirmReceipt(first.trackingNo(), "receipt-key");
  }

  @Test
  void rejectsUnknownCarrierTrackingAndChangedReplay() {
    List<ShipmentItem> items = List.of(new ShipmentItem(1L, 1));
    assertThatThrownBy(
            () ->
                client.createShipment(
                    "ORD-1", "SHP-1", "UNKNOWN", address, items, "unknown-carrier"))
        .isInstanceOf(ExternalDependencyException.class);
    assertThatThrownBy(() -> client.confirmReceipt("missing", "receipt-key"))
        .isInstanceOf(ExternalDependencyException.class);

    client.createShipment("ORD-1", "SHP-1", "MOCK_EXPRESS", address, items, "changed-key");
    assertThatThrownBy(
            () ->
                client.createShipment(
                    "ORD-1", "SHP-2", "MOCK_EXPRESS", address, items, "changed-key"))
        .isInstanceOf(ExternalDependencyException.class)
        .hasMessageContaining("different shipment request");
  }
}
