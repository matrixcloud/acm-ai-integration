package org.acm.os.application.port.in;

import java.util.List;
import org.acm.os.domain.order.Order;
import org.acm.os.domain.shipment.Shipment;

public interface ShipmentUseCase {
  Shipment createShipment(
      String orderNo, String carrierCode, List<ShipmentLine> items, String idempotencyKey);

  Order confirmReceipt(String orderNo, String shipmentNo, String idempotencyKey);

  record ShipmentLine(Long orderItemId, Integer quantity) {}
}
