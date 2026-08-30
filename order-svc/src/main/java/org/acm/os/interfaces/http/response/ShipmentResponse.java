package org.acm.os.interfaces.http.response;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class ShipmentResponse {
  private String shipmentNo;
  private String status;
  private String carrierCode;
  private String trackingNo;
  private LocalDateTime shippedAt;
  private LocalDateTime deliveredAt;
  private List<Item> items;

  @Data
  public static class Item {
    private String orderItemId;
    private Integer quantity;
  }
}
