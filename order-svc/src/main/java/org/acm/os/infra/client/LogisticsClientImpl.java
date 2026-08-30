package org.acm.os.infra.client;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.acm.os.application.port.out.ExternalDependencyException;
import org.acm.os.application.port.out.LogisticsClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
@ConditionalOnProperty(name = "order.adapters.logistics", havingValue = "mock")
public class LogisticsClientImpl implements LogisticsClient {
  private static final Set<String> CARRIERS = Set.of("MOCK_EXPRESS");
  private final MockFailureRegistry failures;
  private final ConcurrentHashMap<String, LogisticsShipment> shipments = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, ShipmentRequest> requests = new ConcurrentHashMap<>();
  private final Set<String> delivered = ConcurrentHashMap.newKeySet();

  public LogisticsClientImpl(MockFailureRegistry failures) {
    this.failures = failures;
  }

  @Override
  public LogisticsShipment createShipment(
      String orderNo,
      String shipmentNo,
      String carrierCode,
      AddressSnapshot address,
      List<ShipmentItem> items,
      String idempotencyKey) {
    failures.check("logistics-create");
    if (!CARRIERS.contains(carrierCode)) {
      throw new ExternalDependencyException("Unknown carrier '%s'".formatted(carrierCode));
    }
    ShipmentRequest request =
        new ShipmentRequest(orderNo, shipmentNo, carrierCode, address, List.copyOf(items));
    ShipmentRequest existing = requests.putIfAbsent(idempotencyKey, request);
    if (existing != null && !existing.equals(request)) {
      throw new ExternalDependencyException(
          "Logistics idempotency key was reused with a different shipment request");
    }
    return shipments.computeIfAbsent(
        idempotencyKey, key -> new LogisticsShipment("MOCK-" + UUID.randomUUID()));
  }

  @Override
  public void confirmReceipt(String trackingNo, String idempotencyKey) {
    failures.check("logistics-confirm");
    boolean known =
        shipments.values().stream().anyMatch(value -> value.trackingNo().equals(trackingNo));
    if (!known) {
      throw new ExternalDependencyException("Unknown tracking number '%s'".formatted(trackingNo));
    }
    delivered.add(trackingNo);
  }

  private record ShipmentRequest(
      String orderNo,
      String shipmentNo,
      String carrierCode,
      AddressSnapshot address,
      List<ShipmentItem> items) {}
}
