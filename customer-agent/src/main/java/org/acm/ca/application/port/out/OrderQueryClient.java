package org.acm.ca.application.port.out;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderQueryClient {

  List<OrderSummary> getRecentOrders(String customerId);

  List<OrderSummary> findByRecipientPhone(String recipientPhone);

  Optional<OrderDetail> findByOrderNo(String orderNo);

  record OrderSummary(
      String orderNo,
      String status,
      BigDecimal payableTotal,
      String currency,
      LocalDateTime createdAt) {}

  record OrderDetail(
      String orderNo,
      String customerId,
      String status,
      String currency,
      BigDecimal itemTotal,
      BigDecimal payableTotal,
      List<DetailItem> items,
      List<DetailShipment> shipments) {}

  record DetailItem(String productName, int quantity, BigDecimal unitPrice) {}

  record DetailShipment(String shipmentNo, String carrierCode, String status) {}
}
