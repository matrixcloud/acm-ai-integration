package org.acm.os.infra.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.acm.os.application.port.out.InsufficientInventoryException;
import org.acm.os.application.port.out.InventoryClient.InventoryItem;
import org.acm.os.application.port.out.InventoryClient.InventoryReservation;
import org.junit.jupiter.api.Test;

class InventoryClientImplTest {

  private final InventoryClientImpl client = new InventoryClientImpl();

  @Test
  void reserveReducesStockAndReleaseRestoresIt() {
    InventoryItem allStock = new InventoryItem("SKU-001", 100);

    InventoryReservation reservation =
        client.reserve("order-1", List.of(allStock), "reserve-key");
    assertThat(reservation.reservationId()).isNotBlank();
    assertThatThrownBy(() -> client.reserve("order-2", List.of(allStock), "reserve-key-2"))
        .isInstanceOf(InsufficientInventoryException.class)
        .hasMessageContaining("available 0");

    client.release(reservation.reservationId(), "release-key");

    assertThat(client.reserve("order-3", List.of(allStock), "reserve-key-3").reservationId())
        .isNotBlank();
  }

  @Test
  void failedReservationDoesNotDeductOtherItems() {
    List<InventoryItem> request =
        List.of(new InventoryItem("SKU-001", 10), new InventoryItem("SKU-002", 101));

    assertThatThrownBy(() -> client.reserve("order-1", request, "reserve-key"))
        .isInstanceOf(InsufficientInventoryException.class)
        .hasMessageContaining("SKU-002");

    assertThat(
            client
                .reserve(
                    "order-2", List.of(new InventoryItem("SKU-001", 100)), "reserve-key-2")
                .reservationId())
        .isNotBlank();
  }

  @Test
  void releaseRejectsUnknownReservation() {
    assertThatThrownBy(() -> client.release("missing", "release-key"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unknown inventory reservation 'missing'");
  }
}
