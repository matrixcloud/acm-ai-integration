package org.acm.os.application.port.out;

import java.util.List;

public interface LogisticsClient {
  LogisticsShipment createShipment(
      String orderNo,
      String shipmentNo,
      String carrierCode,
      AddressSnapshot address,
      List<ShipmentItem> items,
      String idempotencyKey);

  void confirmReceipt(String trackingNo, String idempotencyKey);

  record AddressSnapshot(
      String recipientName,
      String recipientPhone,
      String province,
      String city,
      String district,
      String detailAddress) {}

  record ShipmentItem(String orderItemId, Integer quantity) {}

  record LogisticsShipment(String trackingNo) {}
}
