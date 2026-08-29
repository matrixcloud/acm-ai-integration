package org.acm.os.interfaces.http.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * HTTP response body for {@code POST /orders}.
 *
 * <p>Flattened projection of the created {@link org.acm.os.domain.order.Order}: exposes the
 * persisted id, order number, status, and derived totals plus item lines — but not internal fields
 * like {@code version} or {@code inventoryReservationId}.
 */
@Data
public class CreateOrderResponse {
  private Long id;
  private String orderNo;
  private String customerId;
  private String status;
  private String currency;
  private BigDecimal itemTotal;
  private BigDecimal payableTotal;
  private LocalDateTime createdAt;
  private List<Item> items;

  @Data
  public static class Item {
    private Integer lineNo;
    private String skuId;
    private String productName;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal lineAmount;
  }
}
